package com.axelweinz.hockeyarlivefeed;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.ArrayList;
import java.util.List;

public class Game { // Class to store general information about the game
    private TransformableNode scoreBug;
    private AnchorNode scoreBugNode;

    private TransformableNode goalInfo;
    private AnchorNode goalInfoNode;

    private List shotList = new ArrayList(); // Store all shots that are currently rendered and displayed
    private List ejectionList = new ArrayList(); // Store all ejections that are currently rendered and displayed

    private long gameTime;
    private String homeTeam;
    private String awayTeam;
    private int homeScore = 0;
    private int awayScore = 0;

    public TransformableNode getScoreBug() {
        return this.scoreBug;
    }

    public AnchorNode getScoreBugNode() {
        return this.scoreBugNode;
    }

    public TransformableNode getGoalInfo() {
        return this.goalInfo;
    }

    public AnchorNode getGoalInfoNode() {
        return this.goalInfoNode;
    }

    public List getShotList() {
        return this.shotList;
    }

    public List getEjectionList() {
        return this.ejectionList;
    }

    public long getGameTime() {
        return this.gameTime;
    }

    public String getHomeTeam() {
        return this.homeTeam;
    }

    public String getAwayTeam() {
        return this.awayTeam;
    }

    public int getHomeScore() {
        return this.homeScore;
    }

    public int getAwayScore() {
        return this.awayScore;
    }

    public void setScoreBug(TransformableNode scoreBug) {
        this.scoreBug = scoreBug;
    }

    public void setScoreBugNode(AnchorNode scoreBugNode) {
        this.scoreBugNode = scoreBugNode;
    }

    public void setGoalInfo(TransformableNode goalInfo) {
        this.goalInfo = goalInfo;
    }

    public void setGoalInfoNode(AnchorNode goalInfoNode) {
        this.goalInfoNode = goalInfoNode;
    }

    public void setShotList(List shotList) {
        this.shotList = shotList;
    }

    public void setEjectionList(List ejectionList) {
        this.ejectionList = ejectionList;
    }

    public void setGameTime(long gameTime) {
        this.gameTime = gameTime;
    }

    public void setHomeTeam(String homeTeam) {
        this.homeTeam = homeTeam;
    }

    public void setAwayTeam(String awayTeam) {
        this.awayTeam = awayTeam;
    }

    public void setHomeScore(int homeScore) {
        this.homeScore = homeScore;
    }

    public void setAwayScore(int awayScore) {
        this.awayScore = awayScore;
    }
}
