/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.core.model;

import com.flowpowered.math.imaginary.Quaternionf;
import com.flowpowered.math.matrix.Matrix3f;
import com.flowpowered.math.vector.Vector2f;
import com.flowpowered.math.vector.Vector3f;
import de.bluecolored.bluemap.core.threejs.BufferGeometry;
import de.bluecolored.bluemap.core.threejs.MaterialGroup;

import java.util.ArrayList;
import java.util.List;

@Deprecated
public class Model<T extends Face> {

    private List<T> faces;

    public Model() {
        this.faces = new ArrayList<>();
    }

    /**
     * Merges the given Model into this model<br>
     * Faces are not cloned: So changes to the faces of the previous model will mirror in this model, but adding and removing faces will not.
     */
    public void merge(Model<T> model){
        faces.addAll(model.getFaces());
    }

    public void addFace(T face){
        faces.add(face);
    }

    public void removeFace(T face){
        faces.remove(face);
    }

    public List<T> getFaces(){
        return faces;
    }

    public void rotate(Quaternionf rotation){
        for (Face f : faces){
            f.rotate(rotation);
        }
    }

    public void transform(Matrix3f transformation){
        for (T f : faces){
            f.transform(transformation);
        }
    }

    public void translate(Vector3f translation){
        for (T f : faces){
            f.translate(translation);
        }
    }

    public BufferGeometry toBufferGeometry() {

        //sort faces by material index
        faces.sort((f1, f2) -> (int) Math.signum(f1.getMaterialIndex() - f2.getMaterialIndex()));

        //reorganize all faces into arrays and create material-groups
        int count = faces.size();

        List<MaterialGroup> groups = new ArrayList<>();
        int groupStart = 0;
        int currentMaterialIndex = -1;
        if (count > 0) currentMaterialIndex = faces.get(0).getMaterialIndex();

        float[] position = new float[count * 3 * 3];
        float[] normal = new float[count * 3 * 3];
        float[] color = new float[count * 3 * 3];
        float[] uv = new float[count * 2 * 3];

        for (int itemIndex = 0; itemIndex < count; itemIndex++){
            T f = faces.get(itemIndex);

            if (currentMaterialIndex != f.getMaterialIndex()){
                groups.add(new MaterialGroup(currentMaterialIndex, groupStart * 3, (itemIndex - groupStart) * 3));
                groupStart = itemIndex;
                currentMaterialIndex = f.getMaterialIndex();
            }

            addVector3fToArray( position, f.getP1(),  (itemIndex * 3 + 0) * 3 );
            addVector3fToArray( normal,   f.getN1(),  (itemIndex * 3 + 0) * 3 );
            addVector3fToArray( color,    f.getC1(),  (itemIndex * 3 + 0) * 3 );
            addVector2fToArray( uv,       f.getUv1(), (itemIndex * 3 + 0) * 2 );

            addVector3fToArray( position, f.getP2(),  (itemIndex * 3 + 1) * 3 );
            addVector3fToArray( normal,   f.getN2(),  (itemIndex * 3 + 1) * 3 );
            addVector3fToArray( color,    f.getC2(),  (itemIndex * 3 + 1) * 3 );
            addVector2fToArray( uv,       f.getUv2(), (itemIndex * 3 + 1) * 2 );

            addVector3fToArray( position, f.getP3(),  (itemIndex * 3 + 2) * 3 );
            addVector3fToArray( normal,   f.getN3(),  (itemIndex * 3 + 2) * 3 );
            addVector3fToArray( color,    f.getC3(),  (itemIndex * 3 + 2) * 3 );
            addVector2fToArray( uv,       f.getUv3(), (itemIndex * 3 + 2) * 2 );
        }

        groups.add(new MaterialGroup(currentMaterialIndex, groupStart * 3, (count - groupStart) * 3));

        return new BufferGeometry(
                position,
                normal,
                color,
                uv,
                groups.toArray(new MaterialGroup[groups.size()])
            );
    }

    static void addVector3fToArray(float[] array, Vector3f v, int startIndex){
        array[startIndex] = v.getX();
        array[startIndex + 1] = v.getY();
        array[startIndex + 2] = v.getZ();
    }

    static void addVector2fToArray(float[] array, Vector2f v, int startIndex){
        array[startIndex] = v.getX();
        array[startIndex + 1] = v.getY();
    }

}
