package edu.myrza.todoapp.repos;

import edu.myrza.todoapp.model.entity.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileRepository extends JpaRepository<FileRecord, String> { }
