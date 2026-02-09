package dk.dtu.methods;

import dk.dtu.shared.Config;
import dk.dtu.shared.TupleSpaces;
import org.jspace.RemoteSpace;

import java.util.UUID;

/**
 * Client-side methods for exporting and importing session data
 */
public class DataManagement {
    
    /**
     * Export session data to a file
     * @param requestsUri URI for requests space
     * @param responsesUri URI for responses space
     * @param filePath Path to export file
     * @param onSuccess Callback on success
     * @param onError Callback on error
     */
    public static void exportSession(String requestsUri, String responsesUri, String filePath,
                                     Callback onSuccess, Callback onError) {
        try {
            RemoteSpace requests = new RemoteSpace(requestsUri);
            RemoteSpace responses = new RemoteSpace(responsesUri);
            
            String requestId = UUID.randomUUID().toString();
            
            // Send export request
            requests.put(TupleSpaces.CMD_EXPORT_SESSION, requestId, filePath, "", "", "");
            
            // Wait for response
            Object[] responseData = responses.get(
                    new org.jspace.FormalField(String.class),
                    new org.jspace.ActualField(requestId),
                    new org.jspace.FormalField(String.class),
                    new org.jspace.FormalField(String.class),
                    new org.jspace.FormalField(String.class),
                    new org.jspace.FormalField(String.class));
            
            String status = (String) responseData[0];
            String message = (String) responseData[2];
            
            if (TupleSpaces.RESP_OK.equals(status)) {
                if (onSuccess != null) {
                    onSuccess.invoke(message);
                }
            } else {
                if (onError != null) {
                    onError.invoke(message);
                }
            }
            
        } catch (Exception e) {
            if (onError != null) {
                onError.invoke("Export failed: " + e.getMessage());
            }
            e.printStackTrace();
            if (Config.isConnectionError(e)) {
                Config.handleConnectionError(e);
            }
        }
    }
    
    /**
     * Import session data from a file
     * @param requestsUri URI for requests space
     * @param responsesUri URI for responses space
     * @param filePath Path to import file
     * @param mode Import mode ("replace" or "merge")
     * @param onSuccess Callback on success
     * @param onError Callback on error
     */
    public static void importSession(String requestsUri, String responsesUri, String filePath, String mode,
                                     Callback onSuccess, Callback onError) {
        try {
            RemoteSpace requests = new RemoteSpace(requestsUri);
            RemoteSpace responses = new RemoteSpace(responsesUri);
            
            String requestId = UUID.randomUUID().toString();
            
            // Send import request with mode parameter
            requests.put(TupleSpaces.CMD_IMPORT_SESSION, requestId, filePath, mode, "", "");
            
            // Wait for response
            Object[] responseData = responses.get(
                    new org.jspace.FormalField(String.class),
                    new org.jspace.ActualField(requestId),
                    new org.jspace.FormalField(String.class),
                    new org.jspace.FormalField(String.class),
                    new org.jspace.FormalField(String.class),
                    new org.jspace.FormalField(String.class));
            
            String status = (String) responseData[0];
            String message = (String) responseData[2];
            
            if (TupleSpaces.RESP_OK.equals(status)) {
                if (onSuccess != null) {
                    onSuccess.invoke(message);
                }
            } else {
                if (onError != null) {
                    onError.invoke(message);
                }
            }
            
        } catch (Exception e) {
            if (onError != null) {
                onError.invoke("Import failed: " + e.getMessage());
            }
            e.printStackTrace();
            if (Config.isConnectionError(e)) {
                Config.handleConnectionError(e);
            }
        }
    }
    
    /**
     * Callback interface for async operations
     */
    public interface Callback {
        void invoke(String message);
    }
}