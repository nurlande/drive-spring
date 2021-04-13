package edu.myrza.todoapp.service;

import edu.myrza.todoapp.exceptions.SystemException;
import edu.myrza.todoapp.model.dto.files.FileRecordDto;
import edu.myrza.todoapp.model.entity.*;
import edu.myrza.todoapp.model.enums.EdgeType;
import edu.myrza.todoapp.model.enums.FileType;
import edu.myrza.todoapp.repos.AccessLevelRepository;
import edu.myrza.todoapp.repos.EdgeRepository;
import edu.myrza.todoapp.repos.FileRepository;
import edu.myrza.todoapp.repos.StatusRepository;
import edu.myrza.todoapp.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileService {

    private final FileSystemUtil fileSystemUtil;
    private final StatusRepository statusRepository;
    private final FileRepository fileRepository;
    private final EdgeRepository edgeRepository;
    private final AccessLevelRepository accessLevelRepo;

    @Autowired
    public FileService(
            FileSystemUtil fileSystemUtil,
            StatusRepository statusRepository,
            FileRepository fileRepository,
            EdgeRepository edgeRepository,
            AccessLevelRepository accessLevelRepo)
    {
        this.fileSystemUtil = fileSystemUtil;
        this.statusRepository = statusRepository;
        this.fileRepository = fileRepository;
        this.edgeRepository = edgeRepository;
        this.accessLevelRepo = accessLevelRepo;
    }

    // USER RELATED OPERATIONS

    // TODO : Make sure usernames are unique. (data invariant)
    @Transactional
    public FileRecordDto prepareUserRootFolder(User user) {

        try {
            // Create an actual folder/directory in fyle_system
            String rootFolderName = user.getUsername();
            fileSystemUtil.createUserRootFolder(rootFolderName);

            Status enabled = statusRepository.findByCode(Status.Code.ENABLED);

            // Save a record about the created root folder in db
            FileRecord rootFolderRecord = FileRecord.createFolder(rootFolderName, rootFolderName, user, enabled);

            return toDto(fileRepository.save(rootFolderRecord));
        } catch (IOException ex) {
            throw new SystemException(ex, "Error creating a root folder for a user [" + user.getUsername() + "]");
        }
    }

    // FOLDER/FILE OPERATIONS

    @Transactional
    public void deleteFiles(User user, List<String> ids) {

        Status deleted = statusRepository.findByCode(Status.Code.DELETED);

        for(String id : ids) {

            Optional<FileRecord> optFile = fileRepository.findById(id);
            if(!optFile.isPresent())
                continue;

            FileRecord file = optFile.get();

            // mark the file as 'deleted'
            file.setStatus(deleted);

            // if the file is 'file' then just save it and continue
            if(!file.getFileType().equals(FileType.FOLDER)) {
                fileRepository.save(file);
                continue;
            }

            // if the file is a folder then mark it's sub folders/files as 'deleted'
            Set<FileRecord> descendants = edgeRepository.serveAllDescendants(file.getId());
            for(FileRecord descendant : descendants) {
                descendant.setStatus(deleted);
            }

            descendants.add(file);
            fileRepository.saveAll(descendants);

        }

    }

    @Transactional
    public Optional<FileRecordDto> renameFile(User user, String fileId, String newName) {
        Optional<FileRecord> optFileRecord = fileRepository.findById(fileId);
        if(optFileRecord.isPresent()) {
            FileRecord fileRecord = optFileRecord.get();
            fileRecord.setName(newName);
            fileRepository.save(fileRecord);
            return Optional.of(toDto(fileRecord));
        }

        return Optional.empty();
    }

    // Download multiple files
    @Transactional(readOnly = true)
    public Resource downloadFiles(User user, List<String> ids) throws IOException {

        // create a tree of files/folder you are gonna send back
        List<TreeNode> nodes = buildTree(user, fileRepository.findAllById(ids));

        // use the tree to create appropriate .zip file
        File compressedFile = fileSystemUtil.compressAndReturnFiles(user.getUsername(), nodes);

        // turn the .zip file into resource
        return new FileSystemResource(compressedFile);
    }

    @Transactional
    public List<FileRecordDto> moveFiles(User user, String srcId, String destId, List<String> filesToMove) {

        // Here, we make sure all the entries are unique and are not equal to either source folder or destination folder
        filesToMove = filesToMove.stream()
                                    .distinct()
                                    .filter(fileId -> !fileId.equals(srcId) && !fileId.equals(destId))
                                    .collect(Collectors.toList());

        List<FileRecordDto> result = new ArrayList<>();

        // Here we access all of destination folder's ancestors
        Optional<FileRecord> optDestFolder = fileRepository.findById(destId);
        if(!optDestFolder.isPresent())
            return result;

        FileRecord destFolder = optDestFolder.get();
        Set<FileRecord> newAncestors = Utils.append(edgeRepository.serveAncestors(destFolder.getId()), destFolder);

        for(String fileId : filesToMove) {

            Optional<FileRecord> optFile = fileRepository.findById(fileId);
            if(!optFile.isPresent())
                continue;

            FileRecord file = optFile.get();

            if(file.getFileType().equals(FileType.FILE)) {
                // 1.
                edgeRepository.deleteByDescendant(file);
                // 2.
                List<Edge> newEdges = newAncestors.stream().map(newAncestor -> {
                    Edge newEdge = new Edge();
                    newEdge.setId(UUID.randomUUID().toString());
                    newEdge.setEdgeOwner(user);
                    newEdge.setAncestor(newAncestor);
                    newEdge.setDescendant(file);

                    if(newAncestor.equals(destFolder))
                        newEdge.setEdgeType(EdgeType.DIRECT);
                    else
                        newEdge.setEdgeType(EdgeType.INDIRECT);

                    return newEdge;
                }).collect(Collectors.toList());

                edgeRepository.saveAll(newEdges);
                result.add(toDto(file));
                continue;
            }

            List<Edge> allNewEdges = new ArrayList<>();
            if(file.getFileType().equals(FileType.FOLDER)) {

                // 1.1 Fetch current ancestors
                Set<FileRecord> currentAncestors = edgeRepository.serveAncestors(file.getId());

                // 1.2 Fetch all of the descendants + add the folder itself
                Set<FileRecord> descendants = Utils.append(edgeRepository.serveAllDescendants(file.getId()), file);

                // 1.3 Delete every edge between 'currentAncestors' and 'descendants'
                edgeRepository.deleteByAncestorInAndDescendantIn(currentAncestors, descendants);

                // 2.1
                List<Edge> newEdges = newAncestors.stream()
                                                  .flatMap(newAncestor -> descendants.stream().map(descendant -> {
                                                      Edge newEdge = new Edge();
                                                      newEdge.setId(UUID.randomUUID().toString());
                                                      newEdge.setEdgeOwner(user);
                                                      newEdge.setAncestor(newAncestor);
                                                      newEdge.setDescendant(descendant);

                                                      if(newAncestor.equals(destFolder) && descendant.equals(file))
                                                          newEdge.setEdgeType(EdgeType.DIRECT);
                                                      else
                                                          newEdge.setEdgeType(EdgeType.INDIRECT);

                                                      return newEdge;
                                                  }))
                                                  .collect(Collectors.toList());

                // 2.2
                allNewEdges.addAll(newEdges);
            }

            if(!allNewEdges.isEmpty()) {
                edgeRepository.saveAll(allNewEdges);
                result.add(toDto(file));
            }
        }

        return result;
    }

    // FOLDER OPERATIONS

    @Transactional
    public FileRecordDto createFolder(User user, String parentId, String folderName) {

        Status enabled = statusRepository.findByCode(Status.Code.ENABLED);

        // First we create folderRecord
        FileRecord folderRecord = FileRecord.createFolder(UUID.randomUUID().toString(), folderName, user, enabled);

        FileRecord savedFolderRecord = fileRepository.save(folderRecord);

        // Then we create edges
        // access all of the ancestors of 'parent' folderRecord
        Set<Edge> ancestorsEdges = edgeRepository.serveAncestors(parentId).stream()
                .map(ancestor -> new Edge(UUID.randomUUID().toString(), ancestor, savedFolderRecord, EdgeType.INDIRECT, user))
                .collect(Collectors.toSet());

        // access 'parent' folder
        Edge parentEdge = fileRepository.findById(parentId)
                .map(parent -> new Edge(UUID.randomUUID().toString(), parent, savedFolderRecord, EdgeType.DIRECT, user))
                .orElseThrow(() -> new RuntimeException("No folderRecord with id [" + parentId + "] is found"));

        ancestorsEdges.add(parentEdge);

        //save new edges
        edgeRepository.saveAll(ancestorsEdges);

        return toDto(savedFolderRecord);
    }

    @Transactional(readOnly = true)
    public List<FileRecordDto> serveFolderContent(User user, String folderId) {
        return edgeRepository.serveDescendants(folderId, Arrays.asList(EdgeType.DIRECT, EdgeType.SHARED)).stream()
                             .filter(byUser(user))
                             .map(this::toDto)
                             .collect(Collectors.toList());
    }

    // FILE OPERATIONS

    @Transactional
    public List<FileRecordDto> uploadFiles(User user, String folderId, MultipartFile[] files) {

        // Save files in disk
        List<MultipartFileDecorator> fileDecorators = Stream.of(files)
                .map(mf -> new MultipartFileDecorator(mf, UUID.randomUUID().toString()))
                .collect(Collectors.toList());

        List<MultipartFileDecorator> savedFiles = fileSystemUtil.saveFile(user.getUsername(), fileDecorators);

        // Save records about files
        List<FileRecord> fileRecords = new ArrayList<>();
        for(MultipartFileDecorator savedFile : savedFiles) {
            fileRecords.add(toFile(user, savedFile));
        }
        fileRecords = fileRepository.saveAll(fileRecords);

        // Create and save edges/connection from all of the ancestors to the files (The Closure table)
        FileRecord parent = fileRepository.getOne(folderId);
        Set<FileRecord> ancestors = edgeRepository.serveAncestors(folderId);
        ancestors.add(parent);

        List<Edge> edges = fileRecords.stream()
                                        .flatMap(descendant -> ancestors.stream().map(ancestor -> toEdge(user,parent,ancestor,descendant)))
                                        .collect(Collectors.toList());

        edgeRepository.saveAll(edges);

        return fileRecords.stream().map(this::toDto).collect(Collectors.toList());
    }

    // Download single file
    @Transactional
    public ResourceDecorator downloadFile(User user, String fileId) {

        FileRecord fileRecord = fileRepository.findById(fileId).orElseThrow(() -> new RuntimeException("File [" + fileId + "] not found"));

        // Check if the file has been deleted by owner
        if(fileRecord.getStatus().getCode().equals(Status.Code.DELETED))
            throw new RuntimeException("throw appropriate exception here");

        if(!fileRecord.getOwner().equals(user) && !accessLevelRepo.hasReadOnlyLevel(user, fileRecord))
            throw new RuntimeException("The user has no right to download the file. throw appropriate exception here");

        ResourceDecorator resourceDecorator = new ResourceDecorator();

        File file = fileSystemUtil.serveFile(user.getUsername(), fileId, fileRecord.getExtension());
        resourceDecorator.setResource(new FileSystemResource(file));
        resourceDecorator.setOriginalName(fileRecord.getName());

        return resourceDecorator;
    }

    // HELPER OPERATIONS


    Optional<FileRecord> findById(String id) {
        return fileRepository.findById(id);
    }

    List<FileRecord> getRootFolders(List<User> users) {
        List<String> ids = users.stream().map(User::getUsername).collect(Collectors.toList());
        return fileRepository.findAllById(ids);
    }

    Set<FileRecord> getAllDescendants(FileRecord folder) {
        return edgeRepository.serveAllDescendants(folder.getId());
    }

    void saveEdges(List<Edge> edges) {
        edgeRepository.saveAll(edges);
    }

    private List<TreeNode> buildTree(User user, List<FileRecord> files) {
        List<TreeNode> nodes = new ArrayList<>();

        for(FileRecord file : files) {

            if(file.getStatus().getCode().equals(Status.Code.DELETED))
                continue;

            if(!file.getOwner().equals(user) && !accessLevelRepo.hasReadOnlyLevel(user, file))
                continue;

            if(file.getFileType().equals(FileType.FILE)) {
                FileTreeNode treeNode = new FileTreeNode();
                treeNode.setId(file.getId());
                treeNode.setType(TreeNode.Type.FILE);
                treeNode.setName(file.getName());
                nodes.add(treeNode);
                continue;
            }

            if(file.getFileType().equals(FileType.FOLDER)) {
                FolderTreeNode folderTreeNode = new FolderTreeNode();
                folderTreeNode.setId(file.getId());
                folderTreeNode.setName(file.getName());
                folderTreeNode.setType(TreeNode.Type.FOLDER);

                List<FileRecord> subFiles = edgeRepository.serveDescendants(file.getId(), EdgeType.DIRECT);

                folderTreeNode.setSubnodes(buildTree(user, subFiles));
                nodes.add(folderTreeNode);
            }
        }

        return nodes;
    }

    private String extractExt(String fileOriginalName) {

        if(fileOriginalName == null || fileOriginalName.isEmpty()) return "";

        int lastIndexOfDot = fileOriginalName.lastIndexOf('.');
        if(lastIndexOfDot < 0 || lastIndexOfDot == fileOriginalName.length() - 1) return "";

        return fileOriginalName.substring(lastIndexOfDot);
    }

    private FileRecord toFile(User owner, MultipartFileDecorator savedFile) {
        MultipartFile mFile = savedFile.getMultipartFile();

        Status enabled = statusRepository.findByCode(Status.Code.ENABLED);

        return FileRecord.createFile(
                savedFile.getName(),
                mFile.getOriginalFilename(),
                extractExt(mFile.getOriginalFilename()),
                mFile.getSize(),
                owner, enabled
        );
    }

    private FileRecordDto toDto (FileRecord fileRecord) {
        FileRecordDto dto = new FileRecordDto();
        dto.setId(fileRecord.getId());
        dto.setName(fileRecord.getName());
        dto.setType(fileRecord.getFileType());
        dto.setSize(fileRecord.getSize());
        dto.setLastUpdate(fileRecord.getUpdatedAt());
        return dto;
    }

    private Edge toEdge(User owner, FileRecord parent, FileRecord ancestor, FileRecord descendant) {
        Edge edge = new Edge();
        edge.setId(UUID.randomUUID().toString());
        edge.setAncestor(ancestor);
        edge.setDescendant(descendant);
        edge.setEdgeOwner(owner);
        if (ancestor.equals(parent)) {
            edge.setEdgeType(EdgeType.DIRECT);
        } else {
            edge.setEdgeType(EdgeType.INDIRECT);
        }
        return edge;
    }

    private Predicate<FileRecord> byUser(User user) {
        return file -> file.getOwner().equals(user) || accessLevelRepo.hasReadOnlyLevel(user, file);
    }
}
