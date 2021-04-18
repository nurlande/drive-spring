package edu.myrza.todoapp.controller;

import edu.myrza.todoapp.model.dto.files.FileIdsWrapper;
import edu.myrza.todoapp.model.dto.files.FileRecordDto;
import edu.myrza.todoapp.model.dto.files.MoveFilesReq;
import edu.myrza.todoapp.model.entity.User;
import edu.myrza.todoapp.service.FileService;
import edu.myrza.todoapp.service.UserService;
import edu.myrza.todoapp.util.ResourceDecorator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
public class FileController {

    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String CONTENT_DISPOSITION_ATTACH = "attachment; filename=\"%s\"";

    private final UserService userService;
    private final FileService fileService;

    @Autowired
    public FileController(UserService userService, FileService fileService) {
        this.userService = userService;
        this.fileService = fileService;
    }

    // OPERATIONS APPLIED TO BOTH FILES AND FOLDERS

    @CrossOrigin("*")
    @PostMapping("/file/{fileId}/rename/{newName}")
    public ResponseEntity<?> renameFile(Principal principal, @PathVariable("fileId") String fileId, @PathVariable("newName") String newName) {

        User user = userService.loadUserByUsername(principal.getName());
        Optional<FileRecordDto> optDto = fileService.renameFile(user, fileId, newName);

        return optDto.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @CrossOrigin("*")
    @GetMapping("/files/download")
    public ResponseEntity<Resource> serveFiles(Principal principal, @RequestBody FileIdsWrapper idsWrapper) throws IOException {

        User user = userService.loadUserByUsername(principal.getName());
        Resource resource = fileService.downloadFiles(user, idsWrapper.getFileIds());

        return ResponseEntity.ok()
                .header(CONTENT_DISPOSITION, String.format(CONTENT_DISPOSITION_ATTACH, "files.zip"))
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @CrossOrigin("*")
    @PostMapping("/files/move")
    public List<FileRecordDto> moveFiles(Principal principal, @RequestBody MoveFilesReq req) {

        User user = userService.loadUserByUsername(principal.getName());
        return fileService.moveFiles(user, req.getSrcId(), req.getDestId(), req.getFileIds());
    }

    @CrossOrigin("*")
    @DeleteMapping("/files/delete")
    public void deleteFiles(Principal principal, @RequestBody FileIdsWrapper idsWrapper) {

        User user = userService.loadUserByUsername(principal.getName());
        fileService.deleteFiles(user, idsWrapper.getFileIds());
    }

    // FOLDER OPERATIONS

    @CrossOrigin("*")
    @PostMapping("/folder/{parentFolderId}/new/{newFolderName}")
    public FileRecordDto createFolder(
            Principal principal,
            @PathVariable("parentFolderId") String parentFolderId,
            @PathVariable("newFolderName") String newFolderName)
    {
        User user = userService.loadUserByUsername(principal.getName());
        return fileService.createFolder(user, parentFolderId, newFolderName);
    }

    @CrossOrigin("*")
    @GetMapping("/folder/{folderId}/content")
    public List<FileRecordDto> serveFolderContent(Principal principal, @PathVariable("folderId") String folderId) {
        User user = userService.loadUserByUsername(principal.getName());
        return fileService.serveFolderContent(user, folderId);
    }

    // FILE OPERATIONS

    @CrossOrigin("*")
    @PostMapping("/folder/{folderId}/upload")
    public List<FileRecordDto> uploadFiles(
            Principal principal,
            @PathVariable("folderId") String folderId,
            @ModelAttribute("files") MultipartFile[] files)
    {
        User user = userService.loadUserByUsername(principal.getName());
        return fileService.uploadFiles(user, folderId, files);
    }

    @CrossOrigin("*")
    @GetMapping("/file/download/{fileId}")
    public ResponseEntity<Resource> serveFile(Principal principal, @PathVariable("fileId") String fileId) throws IOException {
        User user = userService.loadUserByUsername(principal.getName());
        ResourceDecorator decorator = fileService.downloadFile(user, fileId);
        Resource resource = decorator.getResource();
        String origName = decorator.getOriginalName();

        return ResponseEntity.ok()
                             .header(CONTENT_DISPOSITION, String.format(CONTENT_DISPOSITION_ATTACH, origName))
                             .contentLength(resource.contentLength())
                             .body(resource);
    }


}
