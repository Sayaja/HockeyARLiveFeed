package com.axelweinz.hockeyarlivefeed;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.database.Exclude;

public class FaceOff {
    public long time;
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

    public FaceOff() {}

    public FaceOff(long time, float xPos, float yPos, float zPos) {
        this.time = time;
        this.xPos = xPos;
        this.yPos = yPos;
        this.zPos = zPos;
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

    @Exclude
    public TransformableNode getModel() {
        return model;
    }

    public void setModel(TransformableNode model) {
        this.model = model;
    }
}
