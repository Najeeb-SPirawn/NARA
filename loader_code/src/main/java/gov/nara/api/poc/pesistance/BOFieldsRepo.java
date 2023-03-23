package gov.nara.api.poc.pesistance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public interface BOFieldsRepo extends JpaRepository<BOFields, Long> {

    List<BOFields> findByBoId(String boId);

    void deleteByBoId(String boId);

    @Query(value = "SELECT count(*) FROM stage_bo_fields WHERE field_key='hRItemId' AND bo_id = ?1", nativeQuery = true)
    Integer getNumberOfItemsByBoId(String boId);

    @Query(value = "SELECT MAX(CAST(seq_id AS integer)) FROM stage_bo_fields WHERE bo_id = ?1 AND field_action= ?2 ", nativeQuery = true)
    Integer getMaxSeqIdByAction(String boId, String action);

    @Query(value = "SELECT field_value FROM stage_bo_fields WHERE bo_id = ?1 AND field_key=?2", nativeQuery = true)
    String getFieldValueByFieldKeyAndBOID(String boId, String fieldKey);

    @Query(value = "SELECT seq_id FROM stage_bo_fields WHERE field_key ='hRItemId' AND field_value=?1", nativeQuery = true)
    String getSeqIdByItemId(String itemId);

}
