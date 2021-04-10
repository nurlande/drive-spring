package edu.myrza.todoapp.util;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class FolderTreeNode extends TreeNode {

    private String id;
    private String name;
    private List<TreeNode> subnodes = new ArrayList<>();
}
