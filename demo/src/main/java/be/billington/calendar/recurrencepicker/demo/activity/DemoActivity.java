package be.billington.calendar.recurrencepicker.demo.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.TextView;

import be.billington.calendar.recurrencepicker.RecurrencePickerDialog;
import be.billington.calendar.recurrencepicker.demo.R;

public class DemoActivity extends FragmentActivity {

    private TextView recurrence;


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.demo);

        recurrence = (TextView) findViewById(R.id.recurrence);

        recurrence.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RecurrencePickerDialog dialog = new RecurrencePickerDialog();
                dialog.setOnRecurrenceSetListener(new RecurrencePickerDialog.OnRecurrenceSetListener() {
                    @Override
                    public void onRecurrenceSet(String rrule) {

                    }
                });
                dialog.show(getSupportFragmentManager(), "recurrencePicker");
            }
        });
    }

}