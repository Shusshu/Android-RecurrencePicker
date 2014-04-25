package be.billington.calendar.recurrencepicker.demo.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.format.Time;
import android.view.View;
import android.widget.TextView;

import be.billington.calendar.recurrencepicker.EventRecurrence;
import be.billington.calendar.recurrencepicker.EventRecurrenceFormatter;
import be.billington.calendar.recurrencepicker.RecurrencePickerDialog;
import be.billington.calendar.recurrencepicker.demo.R;

import java.util.Date;

public class DemoActivity extends FragmentActivity {

    private TextView recurrence;
    private String recurrenceRule;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.demo);

        recurrence = (TextView) findViewById(R.id.recurrence);


        recurrence.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RecurrencePickerDialog recurrencePickerDialog = new RecurrencePickerDialog();

                if (recurrenceRule != null && recurrenceRule.length() > 0) {
                    Bundle bundle = new Bundle();
                    bundle.putString(RecurrencePickerDialog.BUNDLE_RRULE, recurrenceRule);
                    recurrencePickerDialog.setArguments(bundle);
                }

                recurrencePickerDialog.setOnRecurrenceSetListener(new RecurrencePickerDialog.OnRecurrenceSetListener() {
                    @Override
                    public void onRecurrenceSet(String rrule) {
                        recurrenceRule = rrule;

                        if (recurrenceRule != null && recurrenceRule.length() > 0) {
                            EventRecurrence recurrenceEvent = new EventRecurrence();
                            recurrenceEvent.setStartDate(new Time("" + new Date().getTime()));
                            recurrenceEvent.parse(rrule);
                            String srt = EventRecurrenceFormatter.getRepeatString(DemoActivity.this, getResources(), recurrenceEvent, true);
                            recurrence.setText(srt);
                        } else {
                            recurrence.setText("No recurrence");
                        }
                    }
                });
                recurrencePickerDialog.show(getSupportFragmentManager(), "recurrencePicker");
            }
        });
    }

}
