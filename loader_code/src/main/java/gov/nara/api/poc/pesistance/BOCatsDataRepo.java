package gov.nara.api.poc.pesistance;

import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BOCatsDataRepo extends JpaRepository<BOCatsData, Long> {

    // @Query(value = "SELECT date_recd FROM ctrl_cats_data WHERE dal_num = ?1 LIMIT 1", nativeQuery = true)
    // String getAcceptDateByBoId(String boId);

    // @Query(value = "SELECT inactive_dt FROM ctrl_cats_data WHERE dal_num = ?1 LIMIT 1", nativeQuery = true)
    // String getInactiveDateByBoId(String boId);

}
