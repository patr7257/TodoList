package dk.dtu.methods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dk.dtu.net.ApiModels.StateResponse;
import dk.dtu.net.ApiSession;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side data export/import.
 *
 * <p>With the HTTP API there is no server-side session file to export/import.
 * Export therefore writes a client-side snapshot of GET /state to a JSON file
 * (useful for backups/inspection). Import has no bulk endpoint on the API and
 * is reported as unsupported.
 */
public class DataManagement {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    public static void exportSession(String requestsUri, String responsesUri, String filePath,
                                     Callback onSuccess, Callback onError) {
        try {
            StateResponse state = ApiSession.get().fetchState();
            String json = GSON.toJson(state);
            Files.write(Path.of(filePath), json.getBytes(StandardCharsets.UTF_8));
            if (onSuccess != null) {
                onSuccess.invoke("Exported current server state to " + filePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
            ApiSession.get().reportError(e);
            if (onError != null) {
                onError.invoke("Export failed: " + e.getMessage());
            }
        }
    }

    public static void importSession(String requestsUri, String responsesUri, String filePath, String mode,
                                     Callback onSuccess, Callback onError) {
        // The shared API has no bulk import endpoint; importing would need to
        // replay individual create calls, which is out of scope here.
        if (onError != null) {
            onError.invoke("Importing is not supported when connected to the shared API server. "
                    + "Add or edit lists and tasks directly instead.");
        }
    }

    /**
     * Callback interface for async operations
     */
    public interface Callback {
        void invoke(String message);
    }
}
