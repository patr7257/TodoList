package dk.dtu.shared.models;

// Model for persisting task data
public class TaskData {
    private String listId;
    private String taskId;
    private String title;
    private String assignee;
    private String status;
    private String dueDate;
    private Integer priority;
    private Integer year;
    private Integer orderIndex;
    private String location;
    private String description;

    public TaskData() {
    }

    public TaskData(String listId, String taskId, String title, String assignee, String status, String dueDate, Integer priority, Integer orderIndex) {
        this.listId = listId;
        this.taskId = taskId;
        this.title = title;
        this.assignee = assignee;
        this.status = status;
        this.dueDate = dueDate;
        this.priority = priority;
        this.orderIndex = orderIndex;
    }

    public TaskData(String listId, String taskId, String title, String assignee, String status, String dueDate, Integer priority, Integer year, Integer orderIndex) {
        this.listId = listId;
        this.taskId = taskId;
        this.title = title;
        this.assignee = assignee;
        this.status = status;
        this.dueDate = dueDate;
        this.priority = priority;
        this.year = year;
        this.orderIndex = orderIndex;
    }

    public TaskData(String listId, String taskId, String title, String assignee, String status, String dueDate, Integer priority, Integer year, Integer orderIndex, String location, String description) {
        this.listId = listId;
        this.taskId = taskId;
        this.title = title;
        this.assignee = assignee;
        this.status = status;
        this.dueDate = dueDate;
        this.priority = priority;
        this.year = year;
        this.orderIndex = orderIndex;
        this.location = location;
        this.description = description;
    }

    public String getListId() {
        return listId;
    }

    public void setListId(String listId) {
        this.listId = listId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
