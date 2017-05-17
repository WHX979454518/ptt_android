package com.zrt.ptt.app.console.mvp.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by surpass on 2017-4-20.
 */

public class Node {




    /**
     * 节点id
     */
    private String _id;
    /**
     * 父节点id
     */
    private String father;
    /**
     * 是否展开
     */
    private boolean isExpand = false;
    private boolean isChecked = false;
    private boolean isHideChecked = false;
    /**
     * 节点名字
     */
    private String name;
    /**
     * 节点级别
     */
    private int level;
    /**
     * 节点展示图标
     */
    private int icon;
    /**
     * 节点所含的子节点
     */
    private List<Node> childrenNodes = new ArrayList<Node>();
    /**
     * 节点的父节点
     */
    private Node parent;
    private boolean isOnline;//false不在线,true在线
    private boolean isGroup = false;//是否是预定义组,默认不是
    private boolean isSelected = false;//只用于轨迹控制界面Gridviewitem选中

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public void setGroup(boolean group) {
        isGroup = group;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public Node() {
    }

    public Node(String id, String pId, String name,boolean isOnline,boolean isGroup) {
        super();
        this._id = id;
        this.father = pId;
        this.name = name;
        this.isOnline = isOnline;
        this.isGroup = isGroup;
    }


    public String get_id() {
        return _id;
    }

    public void set_id(String _id) {
        this._id = _id;
    }

    public String getFather() {
        return father;
    }

    public void setFather(String father) {
        this.father = father;
    }

    public boolean isExpand() {
        return isExpand;
    }

    /**
     * 当父节点收起，其子节点也收起
     * @param isExpand
     */
    public void setExpand(boolean isExpand) {
        this.isExpand = isExpand;
        if (!isExpand) {

            for (Node node : childrenNodes) {
                node.setExpand(isExpand);
            }
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevel() {
        return parent == null ? 0 : parent.getLevel() + 1;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getIcon() {
        return icon;
    }

    public void setIcon(int icon) {
        this.icon = icon;
    }

    public List<Node> getChildrenNodes() {
        return childrenNodes;
    }

    public void setChildrenNodes(List<Node> childrenNodes) {
        this.childrenNodes = childrenNodes;
    }

    public Node getParent() {
        return parent;
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    /**
     * 判断是否是根节点
     *
     * @return
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * 判断是否是叶子节点
     *
     * @return
     */
    public boolean isLeaf() {
        return childrenNodes.size() == 0;
    }


    /**
     * 判断父节点是否展开
     *
     * @return
     */
    public boolean isParentExpand()
    {
        if (parent == null)
            return false;
        return parent.isExpand();
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean isChecked) {
        this.isChecked = isChecked;
    }

    public boolean isHideChecked() {
        return isHideChecked;
    }

    public void setHideChecked(boolean isHideChecked) {
        this.isHideChecked = isHideChecked;
    }


}
