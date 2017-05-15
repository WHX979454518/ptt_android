package com.zrt.ptt.app.console.mvp.model;

/**
 * Created by surpass on 2017-4-20.
 */

public class OrgNodeBean {

    /**
     * 节点_id
     */
    private String _id;
    /**
     * 节点父father
     */
    private String father;
    /**
     * 节点name
     */
    private String name;
    /**
     *
     */
    private String desc;
    /**
     * 节点名字长度
     */
    private long length;

    private String lastLocationTimeCST;//最新位置时间
    private String positionname;//职位
    private String locateicon;//位置图标？
    private String icon;//头像
    private String password;//密码
    private String phoneNbr;//电话
    private String idNumber;//登录账号
    private int __v;//
    private String enterprise;
    private String mail;
    private Privileges privileges;//权限
    private boolean isParent;//是否是父节点
    private boolean isOnline = false;//false不在线,true在线
    private boolean isChecked = false;//默认不选中数据用于切换时已勾选人员设置数据传值
    private String group_id;//预定义组id,非树状结构
    private boolean isGroup = false;//是否是预定义组,默认不是
    public boolean isGroup() {
        return isGroup;
    }

    public void setGroup(boolean group) {
        isGroup = group;
    }



    public String getGroup_id() {
        return group_id;
    }

    public void setGroup_id(String group_id) {
        this.group_id = group_id;
    }


    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        isChecked = checked;
    }

    public OrgNodeBean(String id, String pId, String name) {
        super();
        this._id = id;
        this.father = pId;
        this.name = name;
    }

    public void OrgNodeBean(String group_id, String name) {
        this.group_id = group_id;
        this.name = name;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public class Privileges {
        private int priority;//权限等级默认3
        private boolean viewMap;//默认true,查看周围人
        private boolean muteAble;//默认true,
        private boolean powerInviteAble;//默认true,
        private boolean calledOuterAble;//默认true,
        private boolean callOuterAble;//默认true,
        private boolean forbidSpeak;//默认true,禁止发言
        private boolean joinAble;//默认true,
        private boolean calledAble;//默认true,
        private boolean groupAble;//默认true,
        private boolean callAble;//默认true,

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public boolean isViewMap() {
            return viewMap;
        }

        public void setViewMap(boolean viewMap) {
            this.viewMap = viewMap;
        }

        public boolean isMuteAble() {
            return muteAble;
        }

        public void setMuteAble(boolean muteAble) {
            this.muteAble = muteAble;
        }

        public boolean isPowerInviteAble() {
            return powerInviteAble;
        }

        public void setPowerInviteAble(boolean powerInviteAble) {
            this.powerInviteAble = powerInviteAble;
        }

        public boolean isCalledOuterAble() {
            return calledOuterAble;
        }

        public void setCalledOuterAble(boolean calledOuterAble) {
            this.calledOuterAble = calledOuterAble;
        }

        public boolean isCallOuterAble() {
            return callOuterAble;
        }

        public void setCallOuterAble(boolean callOuterAble) {
            this.callOuterAble = callOuterAble;
        }

        public boolean isForbidSpeak() {
            return forbidSpeak;
        }

        public void setForbidSpeak(boolean forbidSpeak) {
            this.forbidSpeak = forbidSpeak;
        }

        public boolean isJoinAble() {
            return joinAble;
        }

        public void setJoinAble(boolean joinAble) {
            this.joinAble = joinAble;
        }

        public boolean isCalledAble() {
            return calledAble;
        }

        public void setCalledAble(boolean calledAble) {
            this.calledAble = calledAble;
        }

        public boolean isGroupAble() {
            return groupAble;
        }

        public void setGroupAble(boolean groupAble) {
            this.groupAble = groupAble;
        }

        public boolean isCallAble() {
            return callAble;
        }

        public void setCallAble(boolean callAble) {
            this.callAble = callAble;
        }


    }

    public Privileges getPrivileges() {
        return privileges;
    }

    public void setPrivileges(Privileges privileges) {
        this.privileges = privileges;
    }

    public boolean isParent() {
        return isParent;
    }

    public void setParent(boolean parent) {
        isParent = parent;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public String getLastLocationTimeCST() {
        return lastLocationTimeCST;
    }

    public void setLastLocationTimeCST(String lastLocationTimeCST) {
        this.lastLocationTimeCST = lastLocationTimeCST;
    }

    public String getPositionname() {
        return positionname;
    }

    public void setPositionname(String positionname) {
        this.positionname = positionname;
    }

    public String getLocateicon() {
        return locateicon;
    }

    public void setLocateicon(String locateicon) {
        this.locateicon = locateicon;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhoneNbr() {
        return phoneNbr;
    }

    public void setPhoneNbr(String phoneNbr) {
        this.phoneNbr = phoneNbr;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public int get__v() {
        return __v;
    }

    public void set__v(int __v) {
        this.__v = __v;
    }

    public String getEnterprise() {
        return enterprise;
    }

    public void setEnterprise(String enterprise) {
        this.enterprise = enterprise;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }


}
