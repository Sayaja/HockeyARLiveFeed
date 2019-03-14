package com.axelweinz.hockeyarlivefeed;

import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.GenericTypeIndicator;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {

    private ModelRenderable andyRenderable;
    private ModelRenderable hockeyRinkRenderable;
    private ViewRenderable shotInfoRenderable;
    private ViewRenderable ejectionInfoRenderable;
    private ModelRenderable ejectionModelRenderable;
    private ViewRenderable goalInfoRenderable;
    private ViewRenderable scoreBugRenderable;

    private FirebaseDatabase db = FirebaseDatabase.getInstance();
    private DatabaseReference dbShotsRef = db.getReference("shots");
    private DatabaseReference dbEjectionsRef = db.getReference("ejections");
    private DatabaseReference dbGoalsRef = db.getReference("goals");

    private Game game = new Game(); // Game class that contains all general nodes etc

    private ArFragment arFragment;
    private HitResult firstHit;
    private static final String TAG = MainActivity.class.getSimpleName();
    private Handler handler = new Handler();
    private Runnable runnable = new Runnable() { // Runnable for API call or generate an event
        @Override
        public void run() {
            //event();

            handler.postDelayed(this, 4000);
        }
    };

    private Vector3 rinkPos; // The position of the rink
    private Integer modelCount = 0; // Only 1 rink to be displayed

    private String[] teams = {"Detroit", "Maple Leafs"}; //, "Sharks", "Boston"}; // Placeholder teams
    private String[] playersArray = {"N. Kronwall", "G. Nyquist", "D. Larkin", "T. Bertuzzi", "J. Franzén",
            "A. Matthews", "W. Nylander", "M. Marner", "J. Gardiner", "J. Tavares"}; // Placeholder players
    //, "E. Karlsson", "J. Thornton",
    //"Z. Chára", "B. Marchand"};
    private Map<String, String> players = new HashMap<String, String>(); // Players and their corresponding teams

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Clear Firebase when new game starts
        dbShotsRef.removeValue();
        dbEjectionsRef.removeValue();
        dbGoalsRef.removeValue();

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

        // Assign teams to the players
        int teamCount = 0;
        for (int i=0;i<playersArray.length;i++) {
            players.put(playersArray[i], teams[teamCount]);
            if (i%5 == 4) {
                teamCount += 1;
            }
        }

        // Placeholder teams and score
        game.setHomeTeam("Detroit");
        game.setAwayTeam("Maple Leafs");
        game.setHomeScore(0);
        game.setAwayScore(0);

        ModelRenderable.builder()
                .setSource(this, R.raw.hockeyrinkold)
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

        String tempScore = game.getHomeTeam() + " " + game.getHomeScore() + " - " + game.getAwayScore() + " " + game.getAwayTeam();
        scoreText.setText(tempScore);
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
                        game.setGameTime(System.nanoTime());
                    }

                    if (hockeyRinkRenderable == null) {
                        return;
                    }
                    if (modelCount == 1) { // Limit to 1 model
                        event(); // Generate an event when detected plane is touched
                        return;
                    }

                    // Set up listeners here and have them call the corresponding methods
                    dbShotsRef.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            // This method is called once with the initial value and again
                            // whenever data at this location is updated.
                            long cCount = dataSnapshot.getChildrenCount();
                            long lCount = 1;
                            for (DataSnapshot shotSnapshot: dataSnapshot.getChildren()) {
                                if (lCount >= cCount) {
                                    Shot currShot = shotSnapshot.getValue(Shot.class);
                                    newShot(currShot);
                                }
                                lCount += 1;
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            // Failed to read value
                            Log.w(TAG, "Failed to read value.", error.toException());
                        }
                    });

                    dbEjectionsRef.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            // This method is called once with the initial value and again
                            // whenever data at this location is updated.
                            long cCount = dataSnapshot.getChildrenCount();
                            long lCount = 1;
                            for (DataSnapshot ejectionSnapshot: dataSnapshot.getChildren()) {
                                if (lCount >= cCount) {
                                    Ejection currEjection = ejectionSnapshot.getValue(Ejection.class);
                                    newEjection(currEjection);
                                }
                                lCount += 1;
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            // Failed to read value
                            Log.w(TAG, "Failed to read value.", error.toException());
                        }
                    });

                    dbGoalsRef.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            // This method is called once with the initial value and again
                            // whenever data at this location is updated.
                            long cCount = dataSnapshot.getChildrenCount();
                            long lCount = 1;
                            for (DataSnapshot goalSnapshot: dataSnapshot.getChildren()) {
                                if (lCount >= cCount) {
                                    game.setGoal(goalSnapshot.getValue(Goal.class));
                                    newGoal(game.getGoal());
                                }
                                lCount += 1;
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            // Failed to read value
                            Log.w(TAG, "Failed to read value.", error.toException());
                        }
                    });

                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();
                    Anchor anchor1 = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    game.setScoreBugNode(new AnchorNode(anchor1));

                    anchorNode.setParent(arFragment.getArSceneView().getScene());
                    game.getScoreBugNode().setParent(arFragment.getArSceneView().getScene());

                    // Create the transformable andy and add it to the anchor.
                    TransformableNode hockeyRink = new TransformableNode(arFragment.getTransformationSystem());
                    game.setScoreBug(new TransformableNode(arFragment.getTransformationSystem()));
                    hockeyRink.setRenderable(hockeyRinkRenderable);
                    game.getScoreBug().setRenderable(scoreBugRenderable);

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

                    game.getScoreBug().setLocalPosition(new Vector3(rinkPos.x, rinkPos.y + 0.3f, rinkPos.z - 0.35f));

                    hockeyRink.setParent(anchorNode);
                    game.getScoreBug().setParent(game.getScoreBugNode());

                    hockeyRink.select();

                    // arFragment.getArSceneView().getPlaneRenderer().setVisible(false); // Disable plane visualization
                    arFragment.getArSceneView().getPlaneRenderer().setEnabled(false); // Stop updating planes to fix rink in position
                    modelCount += 1;
                    handler.postDelayed(runnable, 4000); // Start runnable
                });
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
        Vector3 pos = new Vector3(rinkPos.x + rX, rinkPos.y + 0, rinkPos.z + rZ);

        // Remove the goal view when next event occurs (the game has resumed)
        try {
            game.getGoal().getInfo().getScene().onRemoveChild(game.getGoal().getInfo().getParent());
            game.getGoal().getInfo().setRenderable(null);
            game.getGoal().getNode().getAnchor().detach();
        } catch (NullPointerException e) {
        }

        Iterator<Shot> i = game.getShotList().iterator();
        while (i.hasNext()) {
            if (i.next().checkTime()) { // Returns true and is deleted IF enough time has passed. Otherwise, returns false and doesn't delete
                i.remove();
            }
        }
        Iterator<Ejection> j = game.getEjectionList().iterator();
        while (j.hasNext()) {
            if (j.next().checkTime()) { // Returns true and is deleted IF enough time has passed. Otherwise, returns false and doesn't delete
                j.remove();
            }
        }

        int minEvent = 0;
        int maxEvent = 20;
        int randEvent = ThreadLocalRandom.current().nextInt(minEvent,maxEvent + 1);
        //int randEvent = 17;

        // Random event should be generated here
        // Push to Firebase here instead of calling functions
        if (randEvent <= 15) {
            Shot currShot = new Shot(System.nanoTime(), player, team, pos.x, pos.y, pos.z);
            dbShotsRef.push().setValue(currShot);
        } else if (randEvent < 18) {
            Goal currGoal = new Goal(System.nanoTime(), player, team);
            dbGoalsRef.push().setValue(currGoal);
        } else {
            Ejection currEjection = new Ejection(System.nanoTime(), player, team, pos.x, pos.y, pos.z);
            dbEjectionsRef.push().setValue(currEjection);
        }

        //newShot(player, team , position);
        //goal(player, team);
        //newEjection(player, team, position);
    }

    /** Called when a shot event occurs */
    public void newShot(Shot currShot) {

        // Create a TextView programmatically.
        TextView shotText = new TextView(getApplicationContext());

        // Create a LayoutParams for TextView
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView

        // Apply the layout parameters to TextView widget
        shotText.setLayoutParams(lp);

        // Set text to display in TextView
        shotText.setText(currShot.player);

        // Set a text color for TextView text
        if (currShot.team == "Detroit") {
            shotText.setTextColor(Color.parseColor("#CE1126"));
        } else if (currShot.team == "Maple Leafs") {
            shotText.setTextColor(Color.parseColor("#003E7E"));
        } else if (currShot.team == "Sharks") {
            shotText.setTextColor(Color.parseColor("#006D75"));
        } else if (currShot.team == "Boston") {
            shotText.setTextColor(Color.parseColor("#FFB81C"));
        } else {
            shotText.setTextColor(Color.parseColor("#000000"));
        }

        CompletableFuture<ViewRenderable> shotStage =
                ViewRenderable.builder().setView(this, shotText).build();

        CompletableFuture.allOf(
                shotStage)
                .handle(
                        (notUsed, throwable) -> {
                            // When you build a Renderable, Sceneform loads its resources in the background while
                            // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                            // before calling get().

                            if (throwable != null) {
                                return null;
                            }

                            try {
                                shotInfoRenderable = shotStage.get();

                                shotInfoRenderable.setShadowCaster(false);

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
                                currShot.getModel().setLocalPosition(new Vector3(currShot.xPos, currShot.yPos, currShot.zPos));

                                currShot.getInfo().setLocalPosition(new Vector3(currShot.xPos, currShot.yPos + 0.1f, currShot.zPos));

                                currShot.getModel().setParent(currShot.getNode());
                                currShot.getInfo().setParent(currShot.getNode());

                                game.getShotList().add(currShot);
                            } catch (InterruptedException | ExecutionException ex) {
                            }

                            return null;
                        });
    }

    // Called when there is a goal
    public void newGoal(Goal currGoal) {

        // Delete old scoreBug
        game.getScoreBug().getScene().onRemoveChild(game.getScoreBug().getParent());
        game.getScoreBug().setRenderable(null);
        game.getScoreBugNode().getAnchor().detach();

        if (currGoal.team == game.getHomeTeam()) {
            game.setHomeScore(game.getHomeScore() + 1);
        } else if (currGoal.team == game.getAwayTeam()) {
            game.setAwayScore(game.getAwayScore() + 1);
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
        String temp = "GOAL " + currGoal.team + "\n" + currGoal.player + "\n" + String.valueOf(game.getHomeScore()) + " - " + String.valueOf(game.getAwayScore());
        goalText.setText(temp);
        scoreText.setText(game.getHomeTeam() + " " + game.getHomeScore() + " - " + game.getAwayScore() + " " + game.getAwayTeam());

        // Set a text color for TextView text
        if (currGoal.team == "Detroit") {
            goalText.setTextColor(Color.parseColor("#CE1126"));
        } else if (currGoal.team == "Maple Leafs") {
            goalText.setTextColor(Color.parseColor("#003E7E"));
        } else if (currGoal.team == "Sharks") {
            goalText.setTextColor(Color.parseColor("#006D75"));
        } else if (currGoal.team == "Boston") {
            goalText.setTextColor(Color.parseColor("#FFB81C"));
        } else {
            goalText.setTextColor(Color.parseColor("#000000"));
        }
        scoreText.setTextColor(Color.parseColor("#000000"));

        CompletableFuture<ViewRenderable> goalStage =
                ViewRenderable.builder().setView(this, goalText).build();

        CompletableFuture<ViewRenderable> scoreStage =
                ViewRenderable.builder().setView(this, scoreText).build();

        CompletableFuture.allOf(
                scoreStage)
                .handle(
                        (notUsed, throwable) -> {
                            // When you build a Renderable, Sceneform loads its resources in the background while
                            // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                            // before calling get().

                            if (throwable != null) {
                                return null;
                            }

                            try {
                                goalInfoRenderable = goalStage.get();
                                scoreBugRenderable = scoreStage.get();

                                goalInfoRenderable.setShadowCaster(false);
                                scoreBugRenderable.setShadowCaster(false);

                                // Create the Anchor.
                                Anchor anchor = firstHit.createAnchor();
                                Anchor anchor1 = firstHit.createAnchor();
                                game.getGoal().setNode(new AnchorNode(anchor));
                                game.setScoreBugNode(new AnchorNode(anchor1));
                                game.getGoal().getNode().setParent(arFragment.getArSceneView().getScene());
                                game.getScoreBugNode().setParent(arFragment.getArSceneView().getScene());

                                game.getGoal().setInfo(new TransformableNode(arFragment.getTransformationSystem()));
                                game.setScoreBug(new TransformableNode(arFragment.getTransformationSystem()));
                                game.getGoal().getInfo().setRenderable(goalInfoRenderable);
                                game.getScoreBug().setRenderable(scoreBugRenderable);

                                game.getGoal().getInfo().setLocalPosition(new Vector3(rinkPos.x, rinkPos.y + 0.1f, rinkPos.z - 0.35f));
                                game.getScoreBug().setLocalPosition(new Vector3(rinkPos.x, rinkPos.y + 0.3f, rinkPos.z - 0.35f));

                                game.getGoal().getInfo().setParent(game.getGoal().getNode());
                                game.getScoreBug().setParent(game.getScoreBugNode());
                            } catch (InterruptedException | ExecutionException ex) {
                            }

                            return null;
                        });
    }

    public void newEjection(Ejection currEjection) { // Called when a player is ejected from play

        // Create a TextView programmatically.
        TextView ejectionText = new TextView(getApplicationContext());

        // Create a LayoutParams for TextView
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView

        // Apply the layout parameters to TextView widget
        ejectionText.setLayoutParams(lp);

        // Set text to display in TextView
        ejectionText.setText(currEjection.player);

        // Set a text color for TextView text
        if (currEjection.team == "Detroit") {
            ejectionText.setTextColor(Color.parseColor("#CE1126"));
        } else if (currEjection.team == "Maple Leafs") {
            ejectionText.setTextColor(Color.parseColor("#003E7E"));
        } else if (currEjection.team == "Sharks") {
            ejectionText.setTextColor(Color.parseColor("#006D75"));
        } else if (currEjection.team == "Boston") {
            ejectionText.setTextColor(Color.parseColor("#FFB81C"));
        } else {
            ejectionText.setTextColor(Color.parseColor("#000000"));
        }

        CompletableFuture<ViewRenderable> ejectionStage =
                ViewRenderable.builder().setView(this, ejectionText).build();

        CompletableFuture.allOf(
                ejectionStage)
                .handle(
                        (notUsed, throwable) -> {
                            // When you build a Renderable, Sceneform loads its resources in the background while
                            // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                            // before calling get().

                            if (throwable != null) {
                                return null;
                            }

                            try {
                                ejectionInfoRenderable = ejectionStage.get();

                                ejectionInfoRenderable.setShadowCaster(false);

                                // Create the Anchor.
                                currEjection.setAnchor(firstHit.createAnchor());
                                currEjection.setNode(new AnchorNode(currEjection.getAnchor()));
                                currEjection.getNode().setParent(arFragment.getArSceneView().getScene());

                                // Create the transformable andy and add it to the anchor.
                                currEjection.setModel(new TransformableNode(arFragment.getTransformationSystem()));
                                currEjection.getModel().setRenderable(ejectionModelRenderable);

                                currEjection.setInfo(new TransformableNode(arFragment.getTransformationSystem()));
                                currEjection.getInfo().setRenderable(ejectionInfoRenderable);

                                currEjection.getModel().setLocalPosition(new Vector3(currEjection.xPos, currEjection.yPos, currEjection.zPos));

                                currEjection.getInfo().setLocalPosition(new Vector3(currEjection.xPos, currEjection.yPos + 0.1f, currEjection.zPos));

                                currEjection.getModel().setParent(currEjection.getNode());
                                currEjection.getInfo().setParent(currEjection.getNode());

                                game.getEjectionList().add(currEjection);
                            } catch (InterruptedException | ExecutionException ex) {
                            }

                            return null;
                        });
    }
}
