package com.axelweinz.hockeyarlivefeed;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.database.Exclude;

public class Goal {
    public long time;
    public String player;
    public String team;

    @Exclude
    public TransformableNode info;
    @Exclude
    public Anchor anchor;
    @Exclude
    public AnchorNode node;

    public Goal() {}

    public Goal(long time, String player, String team) {
        this.time = time;
        this.player = player;
        this.team = team;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getPlayer() {
        return player;
    }

    public void setPlayer(String player) {
        this.player = player;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    @Exclude
    public TransformableNode getInfo() {
        return info;
    }

    public void setInfo(TransformableNode info) {
        this.info = info;
    }

    @Exclude
    public Anchor getAnchor() {
        return anchor;
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
    }

    @Exclude
    public AnchorNode getNode() {
        return node;
    }

    public void setNode(AnchorNode node) {
        this.node = node;
    }
}
