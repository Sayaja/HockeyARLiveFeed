package com.axelweinz.hockeyarlivefeed;

import android.content.Context;
import android.text.Html;
import android.widget.RelativeLayout;
import android.widget.TextView;

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
    private TransformableNode homePP;
    private AnchorNode homePPNode;
    private TransformableNode awayPP;
    private AnchorNode awayPPNode;

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
    private TextView scoreText;
    private boolean homePPBool = false;
    private boolean awayPPBool = false;
    private TextView homePPText;
    private TextView awayPPText;
    private long homePPStart;
    private long awayPPStart;

    public void setParams(Context context, String homeTeam, String awayTeam, String homeColor, String awayColor) {
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.homeColor = homeColor;
        this.awayColor = awayColor;
        this.scoreText = new TextView(context);
        this.homePPText = new TextView(context);
        this.awayPPText = new TextView(context);

        // Create a LayoutParams for TextView
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView

        // Apply the layout parameters to TextView widget
        this.scoreText.setLayoutParams(lp);
        this.homePPText.setLayoutParams(lp);
        this.awayPPText.setLayoutParams(lp);

        // Set text to display in TextView
        String text = "<font color="+this.homeColor+">" + this.homeTeam + "</font> <font color=#ffffff>" + this.homeScore + " - " + this.awayScore + "</font> <font color="+this.awayColor+">" + this.awayTeam + "</font>";
        scoreText.setText(Html.fromHtml(text));
    }

    public TransformableNode getHomePP() {
        return homePP;
    }

    public void setHomePP(TransformableNode homePP) {
        this.homePP = homePP;
    }

    public AnchorNode getHomePPNode() {
        return homePPNode;
    }

    public void setHomePPNode(AnchorNode homePPNode) {
        this.homePPNode = homePPNode;
    }

    public TransformableNode getAwayPP() {
        return awayPP;
    }

    public void setAwayPP(TransformableNode awayPP) {
        this.awayPP = awayPP;
    }

    public AnchorNode getAwayPPNode() {
        return awayPPNode;
    }

    public void setAwayPPNode(AnchorNode awayPPNode) {
        this.awayPPNode = awayPPNode;
    }

    public TextView getHomePPText() {
        return homePPText;
    }

    public void setHomePPText(TextView homePPText) {
        this.homePPText = homePPText;
    }

    public TextView getAwayPPText() {
        return awayPPText;
    }

    public void setAwayPPText(TextView awayPPText) {
        this.awayPPText = awayPPText;
    }

    public long getHomePPStart() {
        return homePPStart;
    }

    public void setHomePPStart(long homePPStart) {
        this.homePPStart = homePPStart;
    }

    public long getAwayPPStart() {
        return awayPPStart;
    }

    public void setAwayPPStart(long awayPPStart) {
        this.awayPPStart = awayPPStart;
    }

    public boolean isHomePPBool() {
        return homePPBool;
    }

    public void setHomePPBool(boolean homePPBool) {
        this.homePPBool = homePPBool;
    }

    public boolean isAwayPPBool() {
        return awayPPBool;
    }

    public void setAwayPPBool(boolean awayPPBool) {
        this.awayPPBool = awayPPBool;
    }

    public TextView getScoreText() {
        return scoreText;
    }

    public void setScoreText(TextView scoreText) {
        this.scoreText = scoreText;
    }

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
