package dk.dtu;

import dk.dtu.shared.TupleSpaces;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TupleSpacesTest {

    @Test
    public void testSpaceNamesAreNotNull() {
        assertNotNull(TupleSpaces.REQUESTS, "REQUESTS space name should exist");
        assertNotNull(TupleSpaces.RESPONSES, "RESPONSES space name should exist");
        assertNotNull(TupleSpaces.LISTS, "LISTS space name should exist");
        assertNotNull(TupleSpaces.TASKS, "TASKS space name should exist");
        assertNotNull(TupleSpaces.USERS, "USERS space name should exist");
        assertNotNull(TupleSpaces.NOTIFICATIONS, "NOTIFICATIONS space name should exist");
    }

    @Test
    public void testSpaceNamesAreString() {
        assertTrue(TupleSpaces.REQUESTS instanceof String, "REQUESTS should be a string");
        assertTrue(TupleSpaces.RESPONSES instanceof String, "RESPONSES should be a string");
    }

    @Test
    public void testNotificationTypeExists() {
        assertNotNull(TupleSpaces.NOTIFY_DATA_CHANGED, "NOTIFY_DATA_CHANGED constant should exist");
        assertEquals("data_changed", TupleSpaces.NOTIFY_DATA_CHANGED, "NOTIFY_DATA_CHANGED should be 'data_changed'");
    }

    @Test
    public void testCommandsExist() {
        assertNotNull(TupleSpaces.CMD_PING, "CMD_PING should exist");
        assertNotNull(TupleSpaces.CMD_CLIENT_CONNECT, "CMD_CLIENT_CONNECT should exist");
        assertNotNull(TupleSpaces.CMD_USER_LOGIN, "CMD_USER_LOGIN should exist");
        assertNotNull(TupleSpaces.CMD_LIST_CREATE, "CMD_LIST_CREATE should exist");
        assertNotNull(TupleSpaces.CMD_TASK_ADD, "CMD_TASK_ADD should exist");
        assertNotNull(TupleSpaces.CMD_TASK_STATUS, "CMD_TASK_STATUS should exist");
    }

    @Test
    public void testPingCommand() {
        assertEquals("ping", TupleSpaces.CMD_PING, "PING command should be 'ping'");
    }

    @Test
    public void testClientConnectCommand() {
        assertEquals("client_connect", TupleSpaces.CMD_CLIENT_CONNECT,
                "CLIENT_CONNECT command should be 'client_connect'");
    }

    @Test
    public void testClientDisconnectCommand() {
        assertEquals("client_disconnect", TupleSpaces.CMD_CLIENT_DISCONNECT,
                "CLIENT_DISCONNECT command should be 'client_disconnect'");
    }

    @Test
    public void testUserLoginCommand() {
        assertEquals("user_login", TupleSpaces.CMD_USER_LOGIN, "USER_LOGIN command should be 'user_login'");
    }

    @Test
    public void testUserLogoutCommand() {
        assertEquals("user_logout", TupleSpaces.CMD_USER_LOGOUT, "USER_LOGOUT command should be 'user_logout'");
    }

    @Test
    public void testListCreateCommand() {
        assertEquals("list_create", TupleSpaces.CMD_LIST_CREATE, "LIST_CREATE command should be 'list_create'");
    }

    @Test
    public void testTaskAddCommand() {
        assertEquals("task_add", TupleSpaces.CMD_TASK_ADD, "TASK_ADD command should be 'task_add'");
    }

    @Test
    public void testTaskStatusCommand() {
        assertEquals("task_status", TupleSpaces.CMD_TASK_STATUS, "TASK_STATUS command should be 'task_status'");
    }

    @Test
    public void testTaskAssignCommand() {
        assertEquals("task_assign", TupleSpaces.CMD_TASK_ASSIGN, "TASK_ASSIGN command should be 'task_assign'");
    }

    @Test
    public void testListsGetCommand() {
        assertEquals("lists_get", TupleSpaces.CMD_LISTS_GET, "LISTS_GET command should be 'lists_get'");
    }

    @Test
    public void testTasksGetCommand() {
        assertEquals("tasks_get", TupleSpaces.CMD_TASKS_GET, "TASKS_GET command should be 'tasks_get'");
    }

    @Test
    public void testTaskDeleteCommand() {
        assertEquals("task_delete", TupleSpaces.CMD_TASK_DELETE, "TASK_DELETE command should be 'task_delete'");
    }

    @Test
    public void testListDeleteCommand() {
        assertEquals("list_delete", TupleSpaces.CMD_LIST_DELETE, "LIST_DELETE command should be 'list_delete'");
    }

    @Test
    public void testResponseOkStatus() {
        assertEquals("ok", TupleSpaces.RESP_OK, "RESP_OK should be 'ok'");
    }

    @Test
    public void testResponseErrorStatus() {
        assertEquals("error", TupleSpaces.RESP_ERROR, "RESP_ERROR should be 'error'");
    }

    @Test
    public void testArity() {
        assertEquals(6, TupleSpaces.ARITY, "ARITY should be 6");
    }

    @Test
    public void testAllCommandsAreDefined() {
        assertNotNull(TupleSpaces.CMD_PING, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_CLIENT_CONNECT, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_CLIENT_DISCONNECT, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_USER_LOGIN, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_USER_LOGOUT, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_LIST_CREATE, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_TASK_ADD, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_TASK_STATUS, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_TASK_ASSIGN, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_LISTS_GET, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_TASKS_GET, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_TASK_DELETE, "All commands should be defined");
        assertNotNull(TupleSpaces.CMD_LIST_DELETE, "All commands should be defined");
    }

}
