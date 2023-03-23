package gov.nara.api.poc.pesistance;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import gov.nara.api.poc.constants.NARAConstants;

@Entity
@Table(name = "stage_bo_fields", schema = NARAConstants.SCHEMA_NAME)
public class BOFields {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "bo_id")
    private String boId;

    @Column(name = "seq_id")
    private String seqId;

    @Column(name = "field_action")
    private String action;

    @Column(name = "field_key")
    private String fieldKey;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String value;

    public String getBoId() {
        return boId;
    }

    public void setBoId(String boId) {
        this.boId = boId;
    }

    public String getSeqId() {
        return seqId;
    }

    public void setSeqId(String seqId) {
        this.seqId = seqId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

}
