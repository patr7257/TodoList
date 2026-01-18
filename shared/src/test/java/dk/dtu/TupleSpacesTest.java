package dk.dtu;

import dk.dtu.shared.TupleSpaces;
import org.junit.Test;
import static org.junit.Assert.*;

public class TupleSpacesTest {

    @Test
    public void testSpaceNamesAreNotNull() {
        assertNotNull("REQUESTS space name should exist", TupleSpaces.REQUESTS);
        assertNotNull("RESPONSES space name should exist", TupleSpaces.RESPONSES);
        assertNotNull("LISTS space name should exist", TupleSpaces.LISTS);
        assertNotNull("TASKS space name should exist", TupleSpaces.TASKS);
        assertNotNull("USERS space name should exist", TupleSpaces.USERS);
        assertNotNull("NOTIFICATIONS space name should exist", TupleSpaces.NOTIFICATIONS);
    }

    @Test
    public void testSpaceNamesAreString() {
        assertTrue("REQUESTS should be a string", TupleSpaces.REQUESTS instanceof String);
        assertTrue("RESPONSES should be a string", TupleSpaces.RESPONSES instanceof String);
    }

    @Test
    public void testNotificationTypeExists() {
        assertNotNull("NOTIFY_DATA_CHANGED constant should exist", TupleSpaces.NOTIFY_DATA_CHANGED);
        assertEquals("NOTIFY_DATA_CHANGED should be 'data_changed'", "data_changed", TupleSpaces.NOTIFY_DATA_CHANGED);
    }

    @Test
    public void testCommandsExist() {
        assertNotNull("CMD_PING should exist", TupleSpaces.CMD_PING);
        assertNotNull("CMD_CLIENT_CONNECT should exist", TupleSpaces.CMD_CLIENT_CONNECT);
        assertNotNull("CMD_USER_LOGIN should exist", TupleSpaces.CMD_USER_LOGIN);
        assertNotNull("CMD_LIST_CREATE should exist", TupleSpaces.CMD_LIST_CREATE);
        assertNotNull("CMD_TASK_ADD should exist", TupleSpaces.CMD_TASK_ADD);
        assertNotNull("CMD_TASK_STATUS should exist", TupleSpaces.CMD_TASK_STATUS);
    }

    @Test
    public void testPingCommand() {
        assertEquals("PING command should be 'ping'", "ping", TupleSpaces.CMD_PING);
    }

    @Test
    public void testClientConnectCommand() {
        assertEquals("CLIENT_CONNECT command should be 'client_connect'", "client_connect",
                TupleSpaces.CMD_CLIENT_CONNECT);
    }

    @Test
    public void testClientDisconnectCommand() {
        assertEquals("CLIENT_DISCONNECT command should be 'client_disconnect'", "client_disconnect",
                TupleSpaces.CMD_CLIENT_DISCONNECT);
    }

    @Test
    public void testUserLoginCommand() {
        assertEquals("USER_LOGIN command should be 'user_login'", "user_login", TupleSpaces.CMD_USER_LOGIN);
    }

    @Test
    public void testUserLogoutCommand() {
        assertEquals("USER_LOGOUT command should be 'user_logout'", "user_logout", TupleSpaces.CMD_USER_LOGOUT);
    }

    @Test
    public void testListCreateCommand() {
        assertEquals("LIST_CREATE command should be 'list_create'", "list_create", TupleSpaces.CMD_LIST_CREATE);
    }

    @Test
    public void testTaskAddCommand() {
        assertEquals("TASK_ADD command should be 'task_add'", "task_add", TupleSpaces.CMD_TASK_ADD);
    }

    @Test
    public void testTaskStatusCommand() {
        assertEquals("TASK_STATUS command should be 'task_status'", "task_status", TupleSpaces.CMD_TASK_STATUS);
    }

    @Test
    public void testTaskAssignCommand() {
        assertEquals("TASK_ASSIGN command should be 'task_assign'", "task_assign", TupleSpaces.CMD_TASK_ASSIGN);
    }

    @Test
    public void testListsGetCommand() {
        assertEquals("LISTS_GET command should be 'lists_get'", "lists_get", TupleSpaces.CMD_LISTS_GET);
    }

    @Test
    public void testTasksGetCommand() {
        assertEquals("TASKS_GET command should be 'tasks_get'", "tasks_get", TupleSpaces.CMD_TASKS_GET);
    }

    @Test
    public void testTaskDeleteCommand() {
        assertEquals("TASK_DELETE command should be 'task_delete'", "task_delete", TupleSpaces.CMD_TASK_DELETE);
    }

    @Test
    public void testListDeleteCommand() {
        assertEquals("LIST_DELETE command should be 'list_delete'", "list_delete", TupleSpaces.CMD_LIST_DELETE);
    }

    @Test
    public void testResponseOkStatus() {
        assertEquals("RESP_OK should be 'ok'", "ok", TupleSpaces.RESP_OK);
    }

    @Test
    public void testResponseErrorStatus() {
        assertEquals("RESP_ERROR should be 'error'", "error", TupleSpaces.RESP_ERROR);
    }

    @Test
    public void testArity() {
        assertEquals("ARITY should be 6", 6, TupleSpaces.ARITY);
    }

    @Test
    public void testAllCommandsAreDefined() {
        assertNotNull("All commands should be defined", TupleSpaces.CMD_PING);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_CLIENT_CONNECT);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_CLIENT_DISCONNECT);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_USER_LOGIN);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_USER_LOGOUT);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_LIST_CREATE);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_TASK_ADD);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_TASK_STATUS);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_TASK_ASSIGN);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_LISTS_GET);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_TASKS_GET);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_TASK_DELETE);
        assertNotNull("All commands should be defined", TupleSpaces.CMD_LIST_DELETE);
    }

}
