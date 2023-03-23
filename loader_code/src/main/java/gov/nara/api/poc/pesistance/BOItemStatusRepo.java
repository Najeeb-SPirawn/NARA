package gov.nara.api.poc.pesistance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BOItemStatusRepo extends JpaRepository<BOItemStatus, Long> {

    @Query(value = "SELECT status FROM stage_bo_item_status WHERE bo_id = ?1 AND bo_item_id = ?2 ORDER BY id DESC LIMIT 1", nativeQuery = true)
    String getItemStatusByBOIDAndItemId(String boId, String itemId);
}
