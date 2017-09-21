/*
 * Copyright Kirill Morozov 2012
 *
 *
	This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package lab3_205_03.uwaterloo.ca.lab3_205_03;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Pair;
import android.view.View;

/**
 * A simple implementation of a line compass widget.
 * The x axis is not user configurable, but it assumes each sample is
 * happening at a constant frequency.
 *
 * @author Kirill
 *
 */
public class CompassView extends View
{

    private int size=800;
    private int WIDTH = size;
    private int HEIGHT = size;
    private final int AXIS_WIDTH = 100;

    private float theta =0;

    private Paint painter;


    public CompassView(Context context, int size) {
        super(context);
        setBackgroundColor(0xffeeeeee);

        this.size = size;
        WIDTH=size;
        HEIGHT=size;

        painter = new Paint();
        painter.setStrokeWidth(20);
    }


    /*
     * (non-Javadoc)
     * @see android.view.View#onMeasure(int, int)
     */
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec)
    {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(WIDTH + AXIS_WIDTH, HEIGHT);
    }


    /*
     * (non-Javadoc)
     * @see android.view.View#onDraw(android.graphics.Canvas)
     */
    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);

        painter.setColor(Color.BLACK);
        canvas.drawCircle(size/2,size/2,size/2,painter);
        painter.setColor(Color.RED);

        canvas.drawLine(size/2,size/2, (float) (size/2-size/2*Math.sin(Math.toRadians(theta))),(float) (size/2-size/2*Math.cos(Math.toRadians(theta))),painter);
    }

    public void setTheta(float theta) {
        this.theta = theta;
        invalidate();
    }
}
