package gov.nara.api.poc.pesistance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public interface BOUserActionHistoryRepo extends JpaRepository<BOUserActionHistory, Long> {

    @Query(value = "SELECT * FROM stage_bo_user_actions WHERE bo_id = ?1 ORDER BY id ASC", nativeQuery = true)
    List<BOUserActionHistory> findByBoId(String boId);

    void deleteByBoId(String boId);
}
