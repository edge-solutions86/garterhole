package io.github.rkaushikdev.garterhole.striking;

import io.github.rkaushikdev.garterhole.ValidationException;
import io.github.rkaushikdev.garterhole.util.CollectionHelpers;
import io.github.rkaushikdev.garterhole.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Where every blow ought to have fallen.
 *
 * <p>Ringing is not evenly spaced. Rows come in pairs, a handstroke and a
 * backstroke, and there is an extra gap before each handstroke: the handstroke
 * gap, which is what makes ringing sound like ringing rather than like a
 * metronome. The gap is traditionally one blow long, so a whole pull of eight
 * bells takes seventeen blow lengths rather than sixteen.
 *
 * <p>The model is two numbers, the blow interval and the handstroke gap, and
 * every ideal time follows from them.
 *
 * @param stage how many bells strike in each row
 * @param interval milliseconds between one blow and the next within a row
 * @param handstrokeGap the extra milliseconds before each handstroke row
 * @param start when the first row starts, in milliseconds
 */
public record TimingModel(int stage, double interval, double handstrokeGap, double start) {

    /** The number of changes a peal speed is quoted for. */
    public static final int PEAL_CHANGES = 5000;

    private static final double MINUTE = 60000;

    /**
     * @throws ValidationException when the stage, the interval or the gap is not usable
     */
    public TimingModel {
        if (stage < 1) {
            throw new ValidationException("a timing model cannot be built for " + stage + " bells");
        }
        if (!(interval > 0)) {
            throw new ValidationException("a blow interval of " + Json.number(interval) + " is not a length of time");
        }
        if (handstrokeGap < 0) {
            throw new ValidationException(
                    "a handstroke gap of " + Json.number(handstrokeGap) + " is not a length of time");
        }
    }

    /**
     * A model starting at zero whose handstroke gap is one blow long, which is
     * the traditional gap.
     *
     * @param stage how many bells strike in each row
     * @param interval milliseconds between one blow and the next
     * @return the model
     */
    public static TimingModel of(int stage, double interval) {
        return new TimingModel(stage, interval, interval, 0);
    }

    /**
     * A model starting at zero with a handstroke gap of its own.
     *
     * @param stage how many bells strike in each row
     * @param interval milliseconds between one blow and the next
     * @param handstrokeGap the extra milliseconds before each handstroke row
     * @return the model
     */
    public static TimingModel of(int stage, double interval, double handstrokeGap) {
        return new TimingModel(stage, interval, handstrokeGap, 0);
    }

    /**
     * @param moment when the first row starts
     * @return this model, starting at another moment
     */
    public TimingModel startingAt(double moment) {
        return new TimingModel(stage, interval, handstrokeGap, moment);
    }

    /**
     * When a row ought to start.
     *
     * @param rowIndex counted from zero, the first row being a handstroke
     * @return the moment, in milliseconds
     * @throws ValidationException when the row is negative
     */
    public double rowStart(int rowIndex) {
        if (rowIndex < 0) {
            throw new ValidationException("row " + rowIndex + " is not a row of a touch");
        }
        return start + rowIndex * stage * interval + (rowIndex / 2) * handstrokeGap;
    }

    /**
     * When a blow ought to fall.
     *
     * @param rowIndex counted from zero
     * @param place counted from one
     * @return the moment, in milliseconds
     * @throws ValidationException when the place is outside the stage or the row is negative
     */
    public double expectedAt(int rowIndex, int place) {
        if (place < 1 || place > stage) {
            throw new ValidationException("place " + place + " is outside 1.." + stage);
        }
        return rowStart(rowIndex) + (place - 1) * interval;
    }

    /**
     * Every ideal time for a run of rows, in striking order.
     *
     * @param rows how many rows
     * @return the moments, one per blow
     * @throws ValidationException when the number of rows is negative
     */
    public List<Double> idealTimes(int rows) {
        if (rows < 0) {
            throw new ValidationException(rows + " is not a number of rows");
        }
        List<Double> times = new ArrayList<>(rows * stage);
        for (int rowIndex = 0; rowIndex < rows; rowIndex += 1) {
            for (int place = 1; place <= stage; place += 1) {
                times.add(expectedAt(rowIndex, place));
            }
        }
        return times;
    }

    /**
     * @return how long a whole pull takes, which is the number a band feels as the speed
     */
    public double wholePull() {
        return 2 * stage * interval + handstrokeGap;
    }

    /**
     * @return the minutes five thousand changes take at this speed
     */
    public double pealMinutes() {
        return pealMinutes(PEAL_CHANGES);
    }

    /**
     * The peal speed this model implies. Bands talk about their speed this way
     * rather than in milliseconds.
     *
     * @param changes how many changes
     * @return the minutes that many changes take
     */
    public double pealMinutes(int changes) {
        double perRow = wholePull() / 2;
        return (changes * perRow) / MINUTE;
    }

    /**
     * Fit a model to rows that were actually struck.
     *
     * <p>The interval comes from the gaps inside rows, which the handstroke gap
     * does not touch. The handstroke gap is then whatever is left over between a
     * backstroke row and the handstroke that follows it.
     *
     * @param rows the struck rows, at least two
     * @param stage the stage they were rung at
     * @return the fitted model, starting at the first blow
     * @throws ValidationException when there are too few rows or no time between the blows
     */
    public static TimingModel fit(List<StruckRow> rows, int stage) {
        if (rows == null || rows.size() < 2) {
            throw new ValidationException("at least two rows are needed to fit a timing model");
        }
        List<Double> withinRow = new ArrayList<>();
        for (StruckRow row : rows) {
            List<Strike> strikes = row.strikes();
            for (int index = 1; index < strikes.size(); index += 1) {
                withinRow.add(strikes.get(index).at() - strikes.get(index - 1).at());
            }
        }
        OptionalDouble mean = CollectionHelpers.mean(withinRow);
        if (mean.isEmpty() || !(mean.getAsDouble() > 0)) {
            throw new ValidationException("these rows have no measurable blow interval");
        }
        double interval = mean.getAsDouble();
        List<Double> gaps = new ArrayList<>();
        for (int index = 2; index < rows.size(); index += 2) {
            gaps.add(rows.get(index).start() - rows.get(index - 1).end() - interval);
        }
        double handstrokeGap = gaps.isEmpty()
                ? interval
                : Math.max(0, CollectionHelpers.mean(gaps).getAsDouble());
        return new TimingModel(
                stage, interval, handstrokeGap, rows.get(0).strikes().get(0).at());
    }

    /**
     * A model for a band that wants to ring at a given peal speed.
     *
     * @param stage the stage
     * @param minutes how long five thousand changes should take
     * @return the model, with the traditional handstroke gap
     * @throws ValidationException when the minutes are not a positive time
     */
    public static TimingModel forPealSpeed(int stage, double minutes) {
        if (!(minutes > 0)) {
            throw new ValidationException(Json.number(minutes) + " minutes is not a peal speed");
        }
        double perRow = (minutes * MINUTE) / PEAL_CHANGES;
        double interval = (2 * perRow) / (2 * stage + 1);
        return new TimingModel(stage, interval, interval, 0);
    }
}
