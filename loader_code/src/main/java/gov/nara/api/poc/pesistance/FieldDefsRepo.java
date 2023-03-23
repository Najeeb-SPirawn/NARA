package gov.nara.api.poc.pesistance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FieldDefsRepo extends JpaRepository<FieldDefs,String> {
    
}
