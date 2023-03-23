package gov.nara.api.poc.pesistance;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import gov.nara.api.poc.constants.NARAConstants;

@Entity
@Table(name = "stage_bo_item_status", schema = NARAConstants.SCHEMA_NAME)
public class BOItemStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "bo_id")
    private String boId;

    @Column(name = "bo_item_id")
    private String boItemId;

    @Column(name = "status")
    private String status;

}
