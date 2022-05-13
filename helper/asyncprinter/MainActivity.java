package com.example.asyncprinter;

import static org.unitils.reflectionassert.ReflectionAssert.assertReflectionEquals;
import org.apache.commons.lang3.ClassUtils;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.asyncprinter.databinding.ActivityMainBinding;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private String object1;
    private int integer1;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        for (int i = 0; i < 25; i++) {
            System.out.println("MMMbefore:" + System.currentTimeMillis());
            entryFunction1();
            System.out.println("MMMafter:" + System.currentTimeMillis());
        }
        System.out.println("FLOO: Going to print time 25 times.");
        ArrayList<String> al = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            String sig = "Info:" + String.valueOf(i);
            Helper.addInfo(sig);
        }
        System.out.println("FLOO: Finished printing time 25 times.");
        // Context context = getApplicationContext();
        System.out.format("FLOO:Current thread is %s\n", Thread.currentThread().getId());
        try {
            this.someFunction("hello");
            System.out.println("finished calling some Function. if no crash was observed, they are equal.");
        } catch (InstantiationException e) {
            System.out.println("XXXXX: found instantiation exception error");
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            System.out.println("XXXXX: found illegal access exception");
            e.printStackTrace();
        }
    }

    public void entryFunction1() {
        System.out.println("MMMEnter_function:"+System.currentTimeMillis());
        for (int i = 0; i < 100; i++) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("MMMExit_function:"+System.currentTimeMillis());
    }

    public void functionsToCoverMoreConditions() {
        // function calls upto 3-4 depth
        // throwing an exception
        // one log throwing exception, one log not throwing exception

        // 1. what if we have f(g(5)). does this count as two invokes? or do we only add one child_enter/child_exit surrounding this?
        // 2. return f(x). will this work?
        // 3. whenever there is a throw stmt, print to log with a timestamp
        // 4. in every function, add a try catch. the try basically goes around the entire function body. and in the catch, we log saying we caught an exception. and simply throw it again.

        // cases to root-cause
        // 1. in the more outer scope, we see child enter and child exit rather than a parent entry/exit


    }

    public void someFunction(String s) throws InstantiationException, IllegalAccessException {
        // assuming Soot can prefill all this and create this line
        String fn1 = "Function #1";
        ArrayList<Object> l1 = new ArrayList<Object>() {
            {
                add("this is a string primitive");
                add(new ArrayList<Integer>(
                        Arrays.asList(1, 2, 3)));
                add(new ReadState("reading", 1));
                add(new ArrayList<ReadState>(
                        Arrays.asList(new ReadState("read1", 1),
                                new ReadState("read2", 2),
                                new ReadState("read3", 3))));

            }
        };
        String fn2 = "Function #1";
        ArrayList<Object> l2 = new ArrayList<Object>() {
            {
                add("this is a string primitive");
                add(new ArrayList<Integer>(
                        Arrays.asList(1, 2, 3)));
                add(new ReadState("reading", 1));
                add(new ArrayList<ReadState>(
                        Arrays.asList(new ReadState("read1", 1),
                                new ReadState("read2", 2),
                                new ReadState("read3", 3))));
            }
        };
        String fn3 = "Function #3";
        ArrayList<Object> l3 = new ArrayList<Object>() {
            {
                add("this is a string primitive");
                add(new ArrayList<>(
                        Arrays.asList(3, 3, 3)));
                add(new ReadState("reading", 1));
                add(new ArrayList<>(
                        Arrays.asList(new ReadState("read1", 1),
                                new ReadState("read2", 2),
                                new ReadState("read3", 3))));
            }
        };
        ArrayList<Object> l3dupe = new ArrayList<Object>() {
            {
                add("this is a string primitive");
                add(new ArrayList<>(
                        Arrays.asList(3, 3, 3)));
                add(new ReadState("reading", 1));
                add(new ArrayList<>(
                        Arrays.asList(new ReadState("read1", 1),
                                new ReadState("read2", 2),
                                new ReadState("read3", 3))));
            }
        };
        /* System.out.println("asserting now..");
        assertReflectionEquals(l1, l1);
        // not memoizable Function #1
        HelperDeepEquals.addFunctionObj(fn1,l1);
        // should print memoizable Function #1
        HelperDeepEquals.addFunctionObj(fn1,l1);
        // not memoizable Function #3
        HelperDeepEquals.addFunctionObj(fn3,l3);
        // should print memoizable FUnction #3
        HelperDeepEquals.addFunctionObj(fn3,l3dupe); */

        HelperDeepEquals.addFunctionObj(fn1, l1.toArray()); // cannot
        HelperDeepEquals.addFunctionObj(fn1, l1.toArray()); // can
        HelperDeepEquals.addFunctionObj(fn1, l2.toArray()); // can bc l1 == l2
        HelperDeepEquals.addFunctionObj(fn1, l2.toArray()); // can

        // TestObjects
        ArrayList<Object> l4 = new ArrayList<Object>() {
            {
                add(new TestObject(5, "obj4"));
                add(new TestObject(6, "obj3"));
            }
        };
        ArrayList<Object> l5 = new ArrayList<Object>() {
            {
                add(new TestObject(5, "obj5"));
            }
        };
        ArrayList<Object> l6 = new ArrayList<Object>() {
            {
                add(new TestObject(3, "obj6"));
            }
        };
        ArrayList<Object> l6dupe = new ArrayList<Object>() {
            {
                add(new TestObject(3, "obj6"));
            }
        };

        /* HelperDeepEquals.addFunctionObj("fn4", l4); // cannot
        HelperDeepEquals.addFunctionObj("fn5", l5); // cannot
        HelperDeepEquals.addFunctionObj("fn6", l6); // cannot
        HelperDeepEquals.addFunctionObj("fn6", l6); // can
        HelperDeepEquals.addFunctionObj("fn7", l3); // cannot
        HelperDeepEquals.addFunctionObj("fn7", l3dupe); // can
        HelperDeepEquals.addFunctionObj("fn2", l1); // cannot
        HelperDeepEquals.addFunctionObj("fn4", l4); // can */

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}