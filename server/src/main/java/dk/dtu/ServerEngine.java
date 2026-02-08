package dk.dtu;

import dk.dtu.shared.Config;
import dk.dtu.shared.TupleSpaces;
import org.jspace.SequentialSpace;
import org.jspace.SpaceRepository;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-process server runtime that can be started/stopped by a GUI (or CLI).
 */
public final class ServerEngine implements AutoCloseable {

    private final PersistenceService persistenceService;
    private final SpaceRepository repo;

    private final ServerHandlerService handlerService;

    private final SequentialSpace todoLists;
    private final SequentialSpace counter;
    private final SequentialSpace users;
    private final SequentialSpace tasks;
    private final SequentialSpace requests;
    private final SequentialSpace responses;
    private final SequentialSpace notifications;

    private final Thread requestLoopThread;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private ServerEngine(
            PersistenceService persistenceService,
            SpaceRepository repo,
            SequentialSpace todoLists,
            SequentialSpace counter,
            SequentialSpace users,
            SequentialSpace tasks,
            SequentialSpace requests,
            SequentialSpace responses,
            SequentialSpace notifications,
            ServerHandlerService handlerService,
            Thread requestLoopThread
    ) {
        this.persistenceService = Objects.requireNonNull(persistenceService);
        this.repo = Objects.requireNonNull(repo);
        this.todoLists = Objects.requireNonNull(todoLists);
        this.counter = Objects.requireNonNull(counter);
        this.users = Objects.requireNonNull(users);
        this.tasks = Objects.requireNonNull(tasks);
        this.requests = Objects.requireNonNull(requests);
        this.responses = Objects.requireNonNull(responses);
        this.notifications = Objects.requireNonNull(notifications);
        this.handlerService = Objects.requireNonNull(handlerService);
        this.requestLoopThread = Objects.requireNonNull(requestLoopThread);
    }

    /**
     * Start the server using current {@link Config} values.
     */
    public static ServerEngine start() throws Exception {
        PersistenceService persistenceService = new PersistenceService();

        SpaceRepository repo = new SpaceRepository();

        try {
            SequentialSpace todoLists = new SequentialSpace();
            repo.add(TupleSpaces.LISTS, todoLists);
            // Backward-compatible alias (older clients/configs may still use this name).
            repo.add("todoLists", todoLists);

            SequentialSpace counter = new SequentialSpace();
            repo.add("counter", counter);

            SequentialSpace users = new SequentialSpace();
            repo.add(TupleSpaces.USERS, users);

            SequentialSpace tasks = new SequentialSpace();
            repo.add(TupleSpaces.TASKS, tasks);

            SequentialSpace requests = new SequentialSpace();
            repo.add(TupleSpaces.REQUESTS, requests);

            SequentialSpace responses = new SequentialSpace();
            repo.add(TupleSpaces.RESPONSES, responses);

            SequentialSpace notifications = new SequentialSpace();
            repo.add(TupleSpaces.NOTIFICATIONS, notifications);

            boolean sessionLoaded = persistenceService.loadSession(users, todoLists, tasks);
            if (!sessionLoaded) {
                System.out.println("Loading preset database...");
                Database.loadDatabase(users, todoLists, tasks);
                persistenceService.saveSession(users, todoLists, tasks);
            }

            boolean gateOk = repo.addGate(Config.getServerGateUri());
            if (!gateOk) {
                throw new IllegalStateException(
                        "Failed to bind server gate to " + Config.getServerGateUri() + ". " +
                                "Port may already be in use (stale server still running?)."
                );
            }

            counter.put(Database.getTodoListCount(todoLists));

            System.out.println(
                    "Server started.\n" +
                            "- Bind: tcp://" + Config.getServerBindHost() + ":" + Config.getPort() + "/\n" +
                            "- Clients connect to: " + Config.getClientBaseUri() + "\n" +
                            "Waiting for client requests..."
            );

            ServerHandlerService service = new ServerHandlerService(
                    todoLists,
                    counter,
                    users,
                    tasks,
                    requests,
                    responses,
                    notifications,
                    persistenceService
            );

            Thread requestLoop = new Thread(service, "request-loop");
            requestLoop.setDaemon(true);
            requestLoop.start();

            return new ServerEngine(
                    persistenceService,
                    repo,
                    todoLists,
                    counter,
                    users,
                    tasks,
                    requests,
                    responses,
                    notifications,
                    service,
                    requestLoop
            );
        } catch (Exception e) {
            try {
                repo.shutDown();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    public boolean isRunning() {
        return requestLoopThread.isAlive() && !stopped.get();
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }

        try {
            System.out.println("\nShutting down server...");
        } catch (Exception ignored) {
        }

        try {
            requestLoopThread.interrupt();
        } catch (Exception ignored) {
        }

        try {
            handlerService.close();
        } catch (Exception ignored) {
        }

        try {
            persistenceService.saveSession(users, todoLists, tasks);
        } catch (Exception e) {
            System.err.println("Failed to save session during shutdown: " + e.getMessage());
        }

        try {
            // Important: close/unbind TCP gates so clients don't connect to a stale repository.
            repo.shutDown();
        } catch (Exception e) {
            System.err.println("Failed to shut down gate(s): " + e.getMessage());
        }

        try {
            System.out.println("Server stopped.");
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        stop();
    }
}
