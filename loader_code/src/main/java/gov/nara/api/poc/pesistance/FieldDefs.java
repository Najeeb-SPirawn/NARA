package gov.nara.api.poc.pesistance;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import gov.nara.api.poc.constants.NARAConstants;
@Entity
@Table(name = "stage_bo_field_defs", schema = NARAConstants.SCHEMA_NAME)
public class FieldDefs {

    @Id
    @Column(name = "field_key")
    private String key;

    @Column(name = "field_name")
    private String name;

    @Column(name = "is_required")
    private Boolean isRequired;

    @Column(name = "length")
    private Integer length;

    @Column(name = "data")
    private String data;

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Boolean getIsRequired() {
        return isRequired;
    }

    public void setIsRequired(Boolean isRequired) {
        this.isRequired = isRequired;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
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

}
