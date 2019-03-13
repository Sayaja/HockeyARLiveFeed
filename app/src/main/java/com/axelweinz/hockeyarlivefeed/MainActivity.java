package com.axelweinz.hockeyarlivefeed;

import android.graphics.Color;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
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
import java.util.concurrent.ThreadLocalRandom;


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

                    // Read from the database
                    dbShotsRef.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            // This method is called once with the initial value and again
                            // whenever data at this location is updated.
                            //TestClass value = dataSnapshot.child("xd").getValue(TestClass.class);
                            //Log.d(TAG, "ZUP: " + value.getTestString());
                            //Log.d(TAG, "ZAP: " + value.testString);

                            long cCount = dataSnapshot.getChildrenCount();
                            long lCount = 1;
                            for (DataSnapshot postSnapshot: dataSnapshot.getChildren()) {
                                if (lCount >= cCount) {
                                    TestClass post = postSnapshot.getValue(TestClass.class);
                                    Log.d("ZIB", post.getTestString());
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
        Vector3 position = new Vector3(rinkPos.x + rX, rinkPos.y + 0, rinkPos.z + rZ);

        // Remove the goal view when next event occurs (the game has resumed)
        if (game.getGoalInfo() != null) {
            try {
                game.getGoalInfo().getScene().onRemoveChild(game.getGoalInfo().getParent());
                game.getGoalInfo().setRenderable(null);
                game.getGoalInfoNode().getAnchor().detach();
            } catch (NullPointerException e) {
                // Bug: First goal doesn't render
            }
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

        // Random event should be generated here
        if (randEvent <= 15) {
            newShot(player, team, position);
        } else if (randEvent < 18) {
            goal(player, team);
        } else {
            newEjection(player, team, position);
        }

        //newShot(player, team , position);
        //goal(player, team);
        //newEjection(player, team, position);
    }

    /** Called when a shot event occurs */
    public void newShot(String player, String team, Vector3 position) {

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
            shotText.setTextColor(Color.parseColor("#000000"));
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
        game.getShotList().add(currShot);

        TestClass testClass = new TestClass("AMIGO", 0.1234);
        //dbShotsRef.child("xd").setValue(testClass);
        dbShotsRef.push().setValue(testClass);
    }

    // Called when there is a goal
    public void goal(String player, String team) {

        // Delete old scoreBug
        game.getScoreBug().getScene().onRemoveChild(game.getScoreBug().getParent());
        game.getScoreBug().setRenderable(null);
        game.getScoreBugNode().getAnchor().detach();

        if (team == game.getHomeTeam()) {
            game.setHomeScore(game.getHomeScore() + 1);
        } else if (team == game.getAwayTeam()) {
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
        String temp = "GOAL " + team + "\n" + player + "\n" + String.valueOf(game.getHomeScore()) + " - " + String.valueOf(game.getAwayScore());
        goalText.setText(temp);
        scoreText.setText(game.getHomeTeam() + " " + game.getHomeScore() + " - " + game.getAwayScore() + " " + game.getAwayTeam());

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
        Anchor anchor1 = firstHit.createAnchor();
        game.setGoalInfoNode(new AnchorNode(anchor));
        game.setScoreBugNode(new AnchorNode(anchor1));
        game.getGoalInfoNode().setParent(arFragment.getArSceneView().getScene());
        game.getScoreBugNode().setParent(arFragment.getArSceneView().getScene());

        game.setGoalInfo(new TransformableNode(arFragment.getTransformationSystem()));
        game.setScoreBug(new TransformableNode(arFragment.getTransformationSystem()));
        game.getGoalInfo().setRenderable(goalInfoRenderable);
        game.getScoreBug().setRenderable(scoreBugRenderable);

        game.getGoalInfo().setLocalPosition(new Vector3(rinkPos.x, rinkPos.y + 0.1f, rinkPos.z - 0.35f));
        game.getScoreBug().setLocalPosition(new Vector3(rinkPos.x, rinkPos.y + 0.3f, rinkPos.z - 0.35f));

        game.getGoalInfo().setParent(game.getGoalInfoNode());
        game.getScoreBug().setParent(game.getScoreBugNode());
    }

    public void newEjection(String player, String team, Vector3 position) { // Called when a player is ejected from play

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
            ejectionText.setTextColor(Color.parseColor("#000000"));
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
        game.getEjectionList().add(currEjection);
    }
}
