package dk.dtu;

// Main server application
public class ServerMain {

    public static void main(String[] args) {
        try (ServerEngine engine = ServerEngine.start()) {
            Runtime.getRuntime().addShutdownHook(new Thread(engine::stop, "shutdown-hook"));

            // Keep the server process alive.
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(60_000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}