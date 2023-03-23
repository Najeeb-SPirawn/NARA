package gov.nara.api.poc.pesistance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BOSectionsRepo extends JpaRepository<BOSections, Long> {

    @Query(value = "SELECT * FROM stage_bo_sections_defs WHERE type = ?1 and action = ?2 ORDER BY id ASC", nativeQuery = true)
    List<BOSections> selectSectionsByTypeAndAction(String type,String action);

    @Query(value = "SELECT * FROM stage_bo_sections_defs WHERE type = ?1 and action = ?2 and section = ?3 ORDER BY id ASC", nativeQuery = true)
    List<BOSections> selectFieldsByTypeAndActionAndSections(String type,String action,String section);

}
