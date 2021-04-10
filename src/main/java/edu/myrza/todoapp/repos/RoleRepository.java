package edu.myrza.todoapp.repos;

import edu.myrza.todoapp.model.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {
    Set<Role> findByCodeIn(Iterable<Role.Code> code);
}
