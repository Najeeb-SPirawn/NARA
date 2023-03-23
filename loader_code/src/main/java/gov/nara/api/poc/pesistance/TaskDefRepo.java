package gov.nara.api.poc.pesistance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskDefRepo extends JpaRepository<TaskDef,String> {

}
