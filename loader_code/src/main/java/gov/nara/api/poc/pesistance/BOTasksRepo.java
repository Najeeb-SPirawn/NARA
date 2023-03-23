package gov.nara.api.poc.pesistance;

import java.util.List;


import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
@Transactional
public interface BOTasksRepo extends JpaRepository<BOTasks, Long> {

    @Query(value = "SELECT * FROM stage_bo_tasks WHERE bo_id = ?1 ORDER BY id ASC", nativeQuery = true)
    List<BOTasks> findByBoId(String boId);

    void deleteByBoId(String boId);

    @Query(value = "SELECT count(*) FROM stage_bo_tasks WHERE task_key != 'REASSIGN' and bo_id = ?1", nativeQuery = true)
    Integer getNumberTaskByBoId(String boId);

    @Query(value = "SELECT username FROM stage_bo_tasks WHERE bo_id = ?1 and task_key = ?2 limit 1", nativeQuery = true)
    String getUserNameByBOandTask(String boId,String taskKey);
}
