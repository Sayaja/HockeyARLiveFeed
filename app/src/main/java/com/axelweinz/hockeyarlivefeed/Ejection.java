package com.axelweinz.hockeyarlivefeed;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ux.TransformableNode;

public class Ejection { // Class to store information about a rendered ejection
    private long time;
    private TransformableNode info;
    private TransformableNode model;
    private Anchor anchor;
    private AnchorNode node;

    public long getTime() {
        return this.time;
    }

    public TransformableNode getInfo() {
        return this.info;
    }

    public TransformableNode getModel() {
        return this.model;
    }

    public Anchor getAnchor() {
        return this.anchor;
    }

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
                // Bug: the first ejection doesn't render a ViewText and throws this
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
