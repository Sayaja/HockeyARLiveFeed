package com.axelweinz.hockeyarlivefeed;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.database.Exclude;

public class Ejection { // Class to store information about a rendered ejection
    public long time;
    public String player;
    public String team;
    public float xPos;
    public float yPos;
    public float zPos;

    @Exclude
    public TransformableNode info;
    @Exclude
    public TransformableNode model;
    @Exclude
    public Anchor anchor;
    @Exclude
    public AnchorNode node;

    public Ejection() {}

    public Ejection(long time, String player, String team, float xPos, float yPos, float zPos) {
        this.time = time;
        this.player = player;
        this.team = team;
        this.xPos = xPos;
        this.yPos = yPos;
        this.zPos = zPos;
    }

    public long getTime() {
        return this.time;
    }

    @Exclude
    public TransformableNode getInfo() {
        return this.info;
    }

    @Exclude
    public TransformableNode getModel() {
        return this.model;
    }

    @Exclude
    public Anchor getAnchor() {
        return this.anchor;
    }

    @Exclude
    public AnchorNode getNode() {
        return this.node;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public void setInfo(TransformableNode info) {
        this.info = info;
    }

    public void setModel(TransformableNode model) {
        this.model = model;
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
    }

    public void setNode(AnchorNode node) {
        this.node = node;
    }

    public boolean checkTime() { // Removes the rendered shot if it has been displayed for a certain time
        if ((System.nanoTime() - this.time) / 1_000_000_000.0 > 5) {
            try {
                this.info.getScene().onRemoveChild(this.info.getParent());
                this.model.getScene().onRemoveChild(this.model.getParent());
            } catch (NullPointerException e) {
            } finally {
                this.info.setRenderable(null);
                this.model.setRenderable(null);
                this.node.getAnchor().detach();

                return true;
            }
        } else {
            return false;
        }
    }
}
