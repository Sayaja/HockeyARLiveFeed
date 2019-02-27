package com.axelweinz.hockeyarlivefeed;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.filament.Box;
import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TwistGestureRecognizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    class Shot { // Class to store information about a rendered shot
        public long time;
        public TransformableNode info;
        public TransformableNode model;
        public Anchor anchor;
        public AnchorNode node;

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
                    // Bug: the first shot doesn't render a ViewText and throws this
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

    class Ejection { // Class to store information about a rendered ejection
        public long time;
        public TransformableNode info;
        public TransformableNode model;
        public Anchor anchor;
        public AnchorNode node;

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

    private ModelRenderable andyRenderable;
    private ModelRenderable hockeyRinkRenderable;
    private ViewRenderable shotInfoRenderable;
    private ViewRenderable ejectionInfoRenderable;
    private ModelRenderable ejectionModelRenderable;
    private ViewRenderable goalInfoRenderable;
    private ViewRenderable scoreBugRenderable;

    private TransformableNode scoreBug;
    private AnchorNode scoreBugNode;

    private TransformableNode goalInfo;
    private AnchorNode goalInfoNode;

    private List shotList = new ArrayList(); // Store all shots that are currently rendered and displayed
    private List ejectionList = new ArrayList(); // Store all ejections that are currently rendered and displayed

    private ArFragment arFragment;
    private HitResult firstHit;
    private static final String TAG = MainActivity.class.getSimpleName();

    private Vector3 rinkPos; // The position of the rink

    private Integer modelCount = 0;
    private long gameTime;
    private String homeTeam = "Detroit";
    private String awayTeam = "Sharks";
    private int homeScore = 0;
    private int awayScore = 0;
    private String[] teams = {"Detroit", "Maple Leafs", "Sharks", "Boston"};
    private String[] playersArray = {"N. Kronwall", "G. Nyquist", "A. Matthews", "W. Nylander", "E. Karlsson", "J. Thornton",
        "Z. Chára", "B. Marchand"};
    private Map<String, String> players = new HashMap<String, String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

        // Assign teams to the players
        int teamCount = 0;
        for (int i=0;i<playersArray.length;i++) {
            players.put(playersArray[i], teams[teamCount]);
            if (i%2 == 1) {
                teamCount += 1;
            }
        }

        ModelRenderable.builder()
                .setSource(this, R.raw.hockeyrink)
                .build()
                .thenAccept(renderable -> hockeyRinkRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load Renderable.", throwable);
                            return null;
                        });

        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(renderable -> andyRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load Renderable.", throwable);
                            return null;
                        });

        // Create a TextView programmatically.
        TextView scoreText = new TextView(getApplicationContext());

        // Create a LayoutParams for TextView
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView

        // Apply the layout parameters to TextView widget
        scoreText.setLayoutParams(lp);

        scoreText.setText(homeTeam + " " + homeScore + " - " + awayScore + " " + awayTeam);
        scoreText.setTextColor(Color.parseColor("#000000"));

        ViewRenderable.builder()
                .setView(this, scoreText)
                .build()
                .thenAccept(renderable -> {
                    scoreBugRenderable = renderable;
                    renderable.setShadowCaster(false);
                });

        MaterialFactory.makeOpaqueWithColor(getApplicationContext(), new com.google.ar.sceneform.rendering.Color(255,40,0))
                .thenAccept(
                        material -> {
                            Vector3 vector3 = new Vector3(0.05f, 0.05f,0.05f);
                            ejectionModelRenderable = ShapeFactory.makeCube(vector3,
                                    Vector3.zero(), material);
                            ejectionModelRenderable.setShadowCaster(false);
                            ejectionModelRenderable.setShadowReceiver(false);
                        });

        // arFragment.getArSceneView(). <-- To get access to the ARCore session and more
        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (firstHit == null) { // The anchor for the rink. Use this for reference when placing other anchors
                        firstHit = hitResult;
                        gameTime = System.nanoTime();
                    }

//                    while (true) {
//                        arFragment.setOnTapArPlaneListener((HitResult hitResult1, Plane plane1, MotionEvent motionEvent1) -> {
//                        });
//                    }

                    if (hockeyRinkRenderable == null) {
                        return;
                    }
                    if (modelCount == 1) { // Limit to 1 model
                        event(); // Generate an event when detected plane is touched
                        return;
                    }

                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();
                    Anchor anchor1 = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    scoreBugNode = new AnchorNode(anchor1);

                    anchorNode.setParent(arFragment.getArSceneView().getScene());
                    scoreBugNode.setParent(arFragment.getArSceneView().getScene());

                    // Create the transformable andy and add it to the anchor.
                    TransformableNode hockeyRink = new TransformableNode(arFragment.getTransformationSystem());
                    scoreBug = new TransformableNode(arFragment.getTransformationSystem());
                    hockeyRink.setRenderable(hockeyRinkRenderable);
                    scoreBug.setRenderable(scoreBugRenderable);

                    // Set correct rotation of model, then disable rotation with twist
                    hockeyRink.setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), -90f));
                    hockeyRink.getRotationController().setEnabled(false);
                    hockeyRink.getTranslationController().setEnabled(false);
                    hockeyRink.getScaleController().setMinScale(0.4f);
                    hockeyRink.getScaleController().setMaxScale(2.0f);
                    hockeyRink.setLocalScale(new Vector3(0.8f, 0.8f, 0.8f));
                    rinkPos = hockeyRink.getLocalPosition();
                    Vector3 temp = new Vector3(rinkPos.x + 0, rinkPos.y + (rinkPos.y+0)/10f, rinkPos.z + 0);
                    //hockeyRink.setLocalPosition(temp);

                    scoreBug.setLocalPosition(new Vector3(rinkPos.x, rinkPos.y + 0.3f, rinkPos.z - 0.35f));

                    hockeyRink.setParent(anchorNode);
                    scoreBug.setParent(scoreBugNode);

                    hockeyRink.select();

                    // arFragment.getArSceneView().getPlaneRenderer().setVisible(false); // Disable plane visualization
                    arFragment.getArSceneView().getPlaneRenderer().setEnabled(false); // Stop updating planes to fix rink in position
                    modelCount += 1;
                });

//        MotionEvent motionEvent = MotionEvent.obtain(
//                SystemClock.uptimeMillis(),
//                eventTime,
//                MotionEvent.ACTION_UP,
//                x,
//                y,
//                metaState
//        );
//        arFragment.getArSceneView().dispatchTouchEvent(motionEvent);
    }

    // Called when a game event occurs
    public void event() {
        // Generate random shooter
        int minPlayer = 0;
        int maxPlayer = playersArray.length - 1;
        int randPlayer = ThreadLocalRandom.current().nextInt(minPlayer, maxPlayer + 1);
        String player = playersArray[randPlayer];
        String team = players.get(player);

        // Generate a random position of the event
        float minZ = -0.40f;
        float maxZ = -0.05f;
        Random r = new Random();
        float rZ = minZ + r.nextFloat() * (maxZ - minZ);
        float minX = -0.35f;
        float maxX = 0.35f;
        r = new Random();
        float rX = minX + r.nextFloat() * (maxX - minX);
        Vector3 position = new Vector3(rinkPos.x + rX, rinkPos.y + 0, rinkPos.z + rZ);

        // Remove the goal view when next event occurs (the game has resumed)
        if (goalInfo != null) {
            //if ((System.nanoTime() - lastGoalTime)/ 1_000_000_000.0 > 5) {
            goalInfo.getScene().onRemoveChild(goalInfo.getParent());
            goalInfo.setRenderable(null);
            goalInfoNode.getAnchor().detach();
            //}
        }

        Iterator<Shot> i = shotList.iterator();
        while (i.hasNext()) {
            if (i.next().checkTime()) { // Returns true and is deleted IF enough time has passed. Otherwise, returns false and doesn't delete
                i.remove();
            }
        }
        Iterator<Ejection> j = ejectionList.iterator();
        while (j.hasNext()) {
            if (j.next().checkTime()) { // Returns true and is deleted IF enough time has passed. Otherwise, returns false and doesn't delete
                j.remove();
            }
        }

        // Random event should be generated here
        //shot(player, team, position);
        //goal(player, team);
        ejection(player, team, position);
    }

    /** Called when a shot event occurs */
    public void shot(String player, String team, Vector3 position) {

        // Create a TextView programmatically.
        TextView shotText = new TextView(getApplicationContext());

        // Create a LayoutParams for TextView
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView

        // Apply the layout parameters to TextView widget
        shotText.setLayoutParams(lp);

        // Set text to display in TextView
        shotText.setText(player);

        // Set a text color for TextView text
        if (team == "Detroit") {
            shotText.setTextColor(Color.parseColor("#CE1126"));
        } else if (team == "Maple Leafs") {
            shotText.setTextColor(Color.parseColor("#003E7E"));
        } else if (team == "Sharks") {
            shotText.setTextColor(Color.parseColor("#006D75"));
        } else if (team == "Boston") {
            shotText.setTextColor(Color.parseColor("#FFB81C"));
        } else {
            shotText.setTextColor(Color.parseColor("##000000"));
        }

        ViewRenderable.builder()
                .setView(this, shotText)
                .build()
                .thenAccept(renderable -> {
                    shotInfoRenderable = renderable;
                    renderable.setShadowCaster(false);
                });

        Shot currShot = new Shot(); // The current shot

        // Create the Anchor.
        currShot.setAnchor(firstHit.createAnchor());
        currShot.setNode(new AnchorNode(currShot.getAnchor()));
        currShot.getNode().setParent(arFragment.getArSceneView().getScene());

        // Create the transformable andy and add it to the anchor.
        currShot.setModel(new TransformableNode(arFragment.getTransformationSystem()));
        currShot.getModel().setRenderable(andyRenderable);

        currShot.setInfo(new TransformableNode(arFragment.getTransformationSystem()));
        currShot.getInfo().setRenderable(shotInfoRenderable);

        currShot.getModel().getScaleController().setMinScale(0.1f);
        currShot.getModel().getScaleController().setMaxScale(2.0f);
        currShot.getModel().setLocalScale(new Vector3(0.2f, 0.2f, 0.2f));
        //Vector3 pos = andy.getLocalPosition();
        //Vector3 temp = new Vector3(pos.x + 0, pos.y + (pos.y+0)/10f, pos.z + 0);
        currShot.getModel().setLocalPosition(position);

        currShot.getInfo().setLocalPosition(new Vector3(position.x, position.y + 0.1f, position.z));

        currShot.getModel().setParent(currShot.getNode());
        currShot.getInfo().setParent(currShot.getNode());

        currShot.setTime(System.nanoTime());
        shotList.add(currShot);
    }

    // Called when there is a goal
    public void goal(String player, String team) {

        // Delete old scoreBug
        scoreBug.getScene().onRemoveChild(scoreBug.getParent());
        scoreBug.setRenderable(null);
        scoreBugNode.getAnchor().detach();

        if (team == homeTeam) {
            homeScore += 1;
        } else if (team == awayTeam) {
            awayScore += 1;
        }

        // Create a TextView programmatically.
        TextView goalText = new TextView(getApplicationContext());
        TextView scoreText = new TextView(getApplicationContext());

        // Create a LayoutParams for TextView
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView

        // Apply the layout parameters to TextView widget
        goalText.setLayoutParams(lp);
        scoreText.setLayoutParams(lp);

        // Set text to display in TextView
        String temp = "GOAL " + team + "\n" + player + "\n" + String.valueOf(homeScore) + " - " + String.valueOf(awayScore);
        goalText.setText(temp);
        scoreText.setText(homeTeam + " " + homeScore + " - " + awayScore + " " + awayTeam);

        // Set a text color for TextView text
        if (team == "Detroit") {
            goalText.setTextColor(Color.parseColor("#CE1126"));
        } else if (team == "Maple Leafs") {
            goalText.setTextColor(Color.parseColor("#003E7E"));
        } else if (team == "Sharks") {
            goalText.setTextColor(Color.parseColor("#006D75"));
        } else if (team == "Boston") {
            goalText.setTextColor(Color.parseColor("#FFB81C"));
        } else {
            goalText.setTextColor(Color.parseColor("#000000"));
        }
        scoreText.setTextColor(Color.parseColor("#000000"));

        ViewRenderable.builder()
                .setView(this, goalText)
                .build()
                .thenAccept(renderable -> {
                    goalInfoRenderable = renderable;
                    renderable.setShadowCaster(false);
                });

        ViewRenderable.builder()
                .setView(this, scoreText)
                .build()
                .thenAccept(renderable -> {
                    scoreBugRenderable = renderable;
                    renderable.setShadowCaster(false);
                });

        // Create the Anchor.
        Anchor anchor = firstHit.createAnchor();
        goalInfoNode = new AnchorNode(anchor);
        scoreBugNode = new AnchorNode(anchor);
        goalInfoNode.setParent(arFragment.getArSceneView().getScene());
        scoreBugNode.setParent(arFragment.getArSceneView().getScene());

        goalInfo = new TransformableNode(arFragment.getTransformationSystem());
        scoreBug = new TransformableNode(arFragment.getTransformationSystem());
        goalInfo.setRenderable(goalInfoRenderable);
        scoreBug.setRenderable(scoreBugRenderable);

        goalInfo.setLocalPosition(new Vector3(rinkPos.x, rinkPos.y + 0.1f, rinkPos.z - 0.35f));
        scoreBug.setLocalPosition(new Vector3(rinkPos.x, rinkPos.y + 0.3f, rinkPos.z - 0.35f));

        goalInfo.setParent(goalInfoNode);
        scoreBug.setParent(scoreBugNode);
    }

    public void ejection(String player, String team, Vector3 position) { // Called when a player is ejected from play

        // Create a TextView programmatically.
        TextView ejectionText = new TextView(getApplicationContext());

        // Create a LayoutParams for TextView
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView

        // Apply the layout parameters to TextView widget
        ejectionText.setLayoutParams(lp);

        // Set text to display in TextView
        ejectionText.setText(player);

        // Set a text color for TextView text
        if (team == "Detroit") {
            ejectionText.setTextColor(Color.parseColor("#CE1126"));
        } else if (team == "Maple Leafs") {
            ejectionText.setTextColor(Color.parseColor("#003E7E"));
        } else if (team == "Sharks") {
            ejectionText.setTextColor(Color.parseColor("#006D75"));
        } else if (team == "Boston") {
            ejectionText.setTextColor(Color.parseColor("#FFB81C"));
        } else {
            ejectionText.setTextColor(Color.parseColor("##000000"));
        }

        ViewRenderable.builder()
                .setView(this, ejectionText)
                .build()
                .thenAccept(renderable -> {
                    ejectionInfoRenderable = renderable;
                    renderable.setShadowCaster(false);
                });

        Ejection currEjection = new Ejection(); // The current shot

        // Create the Anchor.
        currEjection.setAnchor(firstHit.createAnchor());
        currEjection.setNode(new AnchorNode(currEjection.getAnchor()));
        currEjection.getNode().setParent(arFragment.getArSceneView().getScene());

        // Create the transformable andy and add it to the anchor.
        currEjection.setModel(new TransformableNode(arFragment.getTransformationSystem()));
        currEjection.getModel().setRenderable(ejectionModelRenderable);

        currEjection.setInfo(new TransformableNode(arFragment.getTransformationSystem()));
        currEjection.getInfo().setRenderable(ejectionInfoRenderable);

        currEjection.getModel().setLocalPosition(position);

        currEjection.getInfo().setLocalPosition(new Vector3(position.x, position.y + 0.1f, position.z));

        currEjection.getModel().setParent(currEjection.getNode());
        currEjection.getInfo().setParent(currEjection.getNode());

        currEjection.setTime(System.nanoTime());
        ejectionList.add(currEjection);
    }
}
