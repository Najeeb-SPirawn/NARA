package gov.nara.api.poc.pesistance;

import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BOMigRepo extends JpaRepository<BOMig, String> {

    @Query(value = "SELECT * FROM stage_bo_migration WHERE updated_date between ?1 and ?2", nativeQuery = true)
    List<BOMig> findByUpdatedDateBetween(Date startDate, Date endDate);

//    @Query(value = "SELECT * FROM stage_bo_migration WHERE mig_status = ?1", nativeQuery = true)
//    List<BOMig> findAllBOsByStatus(Integer status);
    
 	List<BOMig> findByMigStatus(Integer status, Pageable pageable);

 	List<BOMig> findByMigStatus(Integer status);
    
 	
 	Integer countByMigStatus(Integer status);

    @Query(value = "SELECT mig_status,count(*) FROM stage_bo_migration GROUP BY mig_status", nativeQuery = true)
    List<Object[]> getBoCountGroupByStatus();

}
