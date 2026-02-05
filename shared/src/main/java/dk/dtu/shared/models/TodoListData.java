package dk.dtu.shared.models;

// Model for persisting todo list data
public class TodoListData {
    private String listId;
    private String listName;
    private int completionPercentage;
    private String owner;
    /** JSON array string of visible task column IDs for this list. */
    private String taskColumnsJson;
    private Integer priority;
    private Integer year;
    private Integer orderIndex;
    private String location;
    private String description;

    public TodoListData() {
    }

    public TodoListData(String listId, String listName, int completionPercentage) {
        this.listId = listId;
        this.listName = listName;
        this.completionPercentage = completionPercentage;
    }

    public TodoListData(String listId, String listName, int completionPercentage, String owner, String taskColumnsJson) {
        this.listId = listId;
        this.listName = listName;
        this.completionPercentage = completionPercentage;
        this.owner = owner;
        this.taskColumnsJson = taskColumnsJson;
    }

    public TodoListData(String listId, String listName, int completionPercentage, String owner, String taskColumnsJson, Integer priority) {
        this.listId = listId;
        this.listName = listName;
        this.completionPercentage = completionPercentage;
        this.owner = owner;
        this.taskColumnsJson = taskColumnsJson;
        this.priority = priority;
    }

    public TodoListData(String listId, String listName, int completionPercentage, String owner, String taskColumnsJson, Integer priority, Integer orderIndex) {
        this.listId = listId;
        this.listName = listName;
        this.completionPercentage = completionPercentage;
        this.owner = owner;
        this.taskColumnsJson = taskColumnsJson;
        this.priority = priority;
        this.orderIndex = orderIndex;
    }

    public TodoListData(String listId, String listName, int completionPercentage, String owner, String taskColumnsJson, Integer priority, Integer year, Integer orderIndex) {
        this.listId = listId;
        this.listName = listName;
        this.completionPercentage = completionPercentage;
        this.owner = owner;
        this.taskColumnsJson = taskColumnsJson;
        this.priority = priority;
        this.year = year;
        this.orderIndex = orderIndex;
    }

    public TodoListData(String listId, String listName, int completionPercentage, String owner, String taskColumnsJson, Integer priority, Integer year, Integer orderIndex, String location, String description) {
        this.listId = listId;
        this.listName = listName;
        this.completionPercentage = completionPercentage;
        this.owner = owner;
        this.taskColumnsJson = taskColumnsJson;
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

    public String getListName() {
        return listName;
    }

    public void setListName(String listName) {
        this.listName = listName;
    }

    public int getCompletionPercentage() {
        return completionPercentage;
    }

    public void setCompletionPercentage(int completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getTaskColumnsJson() {
        return taskColumnsJson;
    }

    public void setTaskColumnsJson(String taskColumnsJson) {
        this.taskColumnsJson = taskColumnsJson;
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
