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

    private List shotList = new ArrayList(); // Store all shots that are currently rendered and displayed
    private List ejectionList = new ArrayList(); // Store all ejections that are currently rendered and displayed
    private Goal goal = new Goal();

    private long gameTime;
    private String homeTeam;
    private String awayTeam;
    private String homeColor;
    private String awayColor;
    private int homeScore = 0;
    private int awayScore = 0;

    public String getHomeColor() {
        return homeColor;
    }

    public void setHomeColor(String homeColor) {
        this.homeColor = homeColor;
    }

    public String getAwayColor() {
        return awayColor;
    }

    public void setAwayColor(String awayColor) {
        this.awayColor = awayColor;
    }

    public Goal getGoal() {
        return goal;
    }

    public void setGoal(Goal goal) {
        this.goal = goal;
    }

    public TransformableNode getScoreBug() {
        return this.scoreBug;
    }

    public AnchorNode getScoreBugNode() {
        return this.scoreBugNode;
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
