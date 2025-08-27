package ru.hollowhorizon.hc.client.utils.math;

public interface MikkTSpaceContext {
    int getNumFaces();

    int getNumVerticesOfFace(int face);

    void getPosition(float[] posOut, int face, int vert);

    void getNormal(float[] normOut, int face, int vert);

    void getTexCoord(float[] texOut, int face, int vert);


    void setTSpaceBasic(float[] tangent, float sign, int face, int vert);

    void setTSpace(float[] tangent, float[] biTangent, float magS, float magT,
                   boolean isOrientationPreserving, int face, int vert);
}
