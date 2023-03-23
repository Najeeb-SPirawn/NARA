package gov.nara.api.poc.pesistance;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import gov.nara.api.poc.constants.NARAConstants;

@Entity
@Table(name = "stage_bo_task_defs", schema = NARAConstants.SCHEMA_NAME)
public class TaskDef {

    @Id
    @Column(name = "task_key")
    private String key;

    @Column(name = "task_name")
    private String name;

    @Column(name = "task_action")
    private String action;

    @Column(name = "base_task_name")
    private String baseTaskName;

    public String getBaseTaskName() {
        return baseTaskName;
    }

    public void setBaseTaskName(String baseTaskName) {
        this.baseTaskName = baseTaskName;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

}
