package main.obj

import main.*
import java.io.File
import java.nio.file.FileSystemException
import java.util.*
import kotlin.collections.ArrayList

/**
 * Created by elect on 21/11/2016.
 */

const val ObjMinSize = 16

class ObjFileImporter : BaseImporter() {

    companion object {

        val desc = AiImporterDesc(
                mName = "Wavefront Object Importer",
                mComments = "surfaces not supported",
                mFlags = AiImporterFlags.SupportTextFlavour.i,
                mFileExtensions = "obj"
        )
    }

    // ------------------------------------------------------------------------------------------------
    //  Returns true, if file is an obj file.
    override fun canRead(pFile: String, checkSig: Boolean): Boolean {

        if (!checkSig)   //Check File Extension
            return pFile.substring(pFile.lastIndexOf('.') + 1) == "obj"
        else //Check file Header
            return false
    }

    // ------------------------------------------------------------------------------------------------
    // Obj-file import implementation
    override fun internReadFile(pFile: String, pScene: AiScene) {

        // Read file into memory
        val file = File(pFile)
        if (!file.canRead()) throw FileSystemException("Failed to open file $pFile.")

        // Get the file-size and validate it, throwing an exception when fails
        val fileSize = file.length().i

        if (fileSize < ObjMinSize) throw Error("OBJ-file is too small.")

        // parse the file into a temporary representation
        val parser = ObjFileParser(file)

        // And create the proper return structures out of it
        createDataFromImport(parser.m_pModel, pScene)
    }

    // ------------------------------------------------------------------------------------------------
    //  Create the data from parsed obj-file
    fun createDataFromImport(pModel: Model, pScene: AiScene) {

        // Create the root node of the scene
        pScene.mRootNode = AiNode()
        if (pModel.m_ModelName.isNotEmpty())
        // Set the name of the scene
            pScene.mRootNode.mName = pModel.m_ModelName
        // This is a fatal error, so break down the application
        else throw Error("pModel.m_ModelName is empty")

        // Create nodes for the whole scene
        val meshArray = ArrayList<AiMesh>()
        for (index in 0 until pModel.m_Objects.size)
            createNodes(pModel, pModel.m_Objects[index], pScene.mRootNode, pScene, meshArray)

        // Create mesh pointer buffer for this scene
        if (pScene.mNumMeshes > 0)
            pScene.mMeshes = meshArray.toMutableList()

        // Create all materials
        createMaterials(pModel, pScene)
    }

    // ------------------------------------------------------------------------------------------------
    //  Creates all nodes of the model
    fun createNodes(pModel: Model, pObject: Object, pParent: AiNode, pScene: AiScene, meshArray: MutableList<AiMesh>): AiNode {

        // Store older mesh size to be able to computes mesh offsets for new mesh instances
        val oldMeshSize = meshArray.size
        val pNode = AiNode()

        pNode.mName = pObject.m_strObjName

        // store the parent
        appendChildToParentNode(pParent, pNode)

        for (meshId in pObject.m_Meshes) {
            val pMesh = createTopology(pModel, pObject, meshId)
            if (pMesh != null && pMesh.mNumFaces > 0)
                meshArray.add(pMesh)
        }

        // Create all nodes from the sub-objects stored in the current object
        if (pObject.m_SubObjects.isNotEmpty()) {

            val numChilds = pObject.m_SubObjects.size
            pNode.mNumChildren = numChilds
            pNode.mChildren = mutableListOf()
            pNode.mNumMeshes = 1
            pNode.mMeshes = IntArray(1)
        }

        // Set mesh instances into scene- and node-instances
        val meshSizeDiff = meshArray.size - oldMeshSize
        if (meshSizeDiff > 0) {
            pNode.mMeshes = IntArray(meshSizeDiff)
            pNode.mNumMeshes = meshSizeDiff
            var index = 0
            for (i in oldMeshSize until meshArray.size) {
                pNode.mMeshes!![index] = pScene.mNumMeshes
                pScene.mNumMeshes++
                index++
            }
        }

        return pNode
    }

    // ------------------------------------------------------------------------------------------------
    //  Appends this node to the parent node
    fun appendChildToParentNode(pParent: AiNode, pChild: AiNode) {

        // Assign parent to child
        pChild.mParent = pParent

        pParent.mNumChildren++
        pParent.mChildren.add(pChild)
    }

    // ------------------------------------------------------------------------------------------------
    //  Create topology data
    fun createTopology(pModel: Model, pData: Object, meshIndex: Int): AiMesh? {

        // Create faces
        val pObjMesh = pModel.m_Meshes[meshIndex]

        if (pObjMesh.m_Faces.isEmpty()) return null

        val pMesh = AiMesh()
        if (pObjMesh.m_name.isNotEmpty()) pMesh.mName = pObjMesh.m_name

        pObjMesh.m_Faces.forEach {
            when (it.m_PrimitiveType) {
                AiPrimitiveType.LINE -> {
                    pMesh.mNumFaces += it.m_vertices.size - 1
                    pMesh.mPrimitiveTypes = pMesh.mPrimitiveTypes or AiPrimitiveType.LINE
                }
                AiPrimitiveType.POINT -> {
                    pMesh.mNumFaces += it.m_vertices.size
                    pMesh.mPrimitiveTypes = pMesh.mPrimitiveTypes or AiPrimitiveType.POINT
                }
                else -> {
                    pMesh.mNumFaces++
                    pMesh.mPrimitiveTypes = pMesh.mPrimitiveTypes or
                            if (it.m_vertices.size > 3) AiPrimitiveType.POLYGON
                            else AiPrimitiveType.TRIANGLE
                }
            }
        }

        var uiIdxCount = 0
        if (pMesh.mNumFaces > 0) {

            val faces = ArrayList<AiFace>()

            if (pObjMesh.m_uiMaterialIndex != Mesh.NoMaterial)
                pMesh.mMaterialIndex = pObjMesh.m_uiMaterialIndex

            var outIndex = 0

            // Copy all data from all stored meshes
            pObjMesh.m_Faces.forEach {
                val face = ArrayList<Int>()
                when (it.m_PrimitiveType) {
                    AiPrimitiveType.LINE -> for (i in 0 until it.m_vertices.size - 1) {
                        val mNumIndices = 2
                        uiIdxCount += mNumIndices
                        repeat(mNumIndices, { face.add(0) })
                    }
                    AiPrimitiveType.POINT -> for (i in 0 until it.m_vertices.size) {
                        val mNumIndices = 1
                        uiIdxCount += mNumIndices
                        repeat(mNumIndices, { face.add(0) })
                    }
                    else -> {
                        val uiNumIndices = it.m_vertices.size
                        uiIdxCount += uiNumIndices
                        repeat(uiNumIndices, { face.add(0) })
                    }
                }
                faces.add(face)
            }
            pMesh.mFaces = faces
        }

        // Create mesh vertices
        createVertexArray(pModel, pData, meshIndex, pMesh, uiIdxCount)

        return pMesh
    }

    // ------------------------------------------------------------------------------------------------
    //  Creates a vertex array
    fun createVertexArray(pModel: Model, pCurrentObject: Object, uiMeshIndex: Int, pMesh: AiMesh, numIndices: Int) {

        // Break, if no faces are stored in object
        if (pCurrentObject.m_Meshes.isEmpty()) return

        // Get current mesh
        val pObjMesh = pModel.m_Meshes[uiMeshIndex]
        if (pObjMesh.m_uiNumIndices < 1) return

        // Copy vertices of this mesh instance
        pMesh.mNumVertices = numIndices
        if (pMesh.mNumVertices == 0)
            throw Error("OBJ: no vertices")
        else if (pMesh.mNumVertices > AI_MAX_ALLOC(main.AiVector3D.SIZE))
            throw Error("OBJ: Too many vertices, would run out of memory")

        pMesh.mVertices = Array(pMesh.mNumVertices, { AiVector3D() }).toMutableList()

        // Allocate buffer for normal vectors
        if (pModel.m_Normals.isNotEmpty() && pObjMesh.m_hasNormals)
            pMesh.mNormals = Array(pMesh.mNumVertices, { AiVector3D() }).toMutableList()

        // Allocate buffer for vertex-color vectors
        if (pModel.m_VertexColors.isNotEmpty())
            pMesh.mColors[0] = Array(pMesh.mNumVertices, { AiColor4D() }).toMutableList()

        // Allocate buffer for texture coordinates
        if (pModel.m_TextureCoord.isNotEmpty() && pObjMesh.m_uiUVCoordinates[0] != 0) {
            pMesh.mNumUVComponents[0] = 2
            pMesh.mTextureCoords[0] = Array(pMesh.mNumVertices, { AiVector3D() })
        }

        // Copy vertices, normals and textures into aiMesh instance
        var newIndex = 0
        var outIndex = 0
        pObjMesh.m_Faces.forEach {

            // Copy all index arrays
            var outVertexIndex = 0
            for (vertexIndex in 0 until it.m_vertices.size) {
                val vertex = it.m_vertices[vertexIndex]
                if (vertex >= pModel.m_Vertices.size)
                    throw Error("OBJ: vertex index out of range")

                pMesh.mVertices[newIndex] = pModel.m_Vertices[vertex]

                // Copy all normals
                if (pModel.m_Normals.isNotEmpty() && vertexIndex in it.m_normals.indices) {
                    val normal = it.m_normals[vertexIndex]
                    if (normal >= pModel.m_Normals.size)
                        throw Error("OBJ: vertex normal index out of range")
                    pMesh.mNormals[newIndex] = pModel.m_Normals[normal]
                }

                // Copy all vertex colors
                if (pModel.m_TextureCoord.isNotEmpty() && vertexIndex in it.m_texturCoords) {

                    val tex = it.m_texturCoords[vertexIndex]
                    assert(tex < pModel.m_TextureCoord.size)

                    if (tex >= pModel.m_TextureCoord.size)
                        throw Error("OBJ: texture coordinate index out of range")

                    val coord3d = pModel.m_TextureCoord[tex]
                    pMesh.mTextureCoords[0]!![newIndex] = main.AiVector3D(coord3d)
                }

                if (pMesh.mNumVertices <= newIndex)
                    throw Error("OBJ: bad vertex index")

                // Get destination face
                val pDestFace = pMesh.mFaces[outIndex]

                val last = (vertexIndex == it.m_vertices.size - 1)
                if (it.m_PrimitiveType != AiPrimitiveType.LINE || !last) {
                    pDestFace[outVertexIndex] = newIndex
                    outVertexIndex++
                }

                when (it.m_PrimitiveType) {

                    AiPrimitiveType.POINT -> {
                        outIndex++
                        outVertexIndex = 0
                    }
                    AiPrimitiveType.LINE -> {
                        outVertexIndex = 0

                        if (!last) outIndex++

                        if (vertex != 0) {
                            if (!last) {
                                pMesh.mVertices[newIndex + 1] = pMesh.mVertices[newIndex]
                                if (it.m_normals.isNotEmpty() && pModel.m_Normals.isNotEmpty())
                                    pMesh.mNormals[newIndex + 1] = pMesh.mNormals[newIndex]

                                if (pModel.m_TextureCoord.isNotEmpty())
                                    for (i in 0 until pMesh.getNumUVChannels())
                                        pMesh.mTextureCoords[i]!![newIndex + 1] = pMesh.mTextureCoords[i]!![newIndex]
                                ++newIndex
                            }
                            // TODO pDestFace[-1].mIndices[1] = newIndex;
                        }
                    }
                    else -> if (last) outIndex++
                }
                ++newIndex
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    //  Creates the material
    fun createMaterials(pModel: Model, pScene: AiScene) {

        val numMaterials = pModel.m_MaterialLib.size
        pScene.mNumMaterials = 0
        if (pModel.m_MaterialLib.isEmpty()) {
            System.err.println("OBJ: no materials specified")
            return
        }

        pScene.mMaterials = ArrayList<AiMaterial>(numMaterials)
        for (matIndex in 0 until numMaterials) {

            // Store material name
            val pCurrentMaterial = pModel.m_MaterialMap[pModel.m_MaterialLib[matIndex]]

            // No material found, use the default material
            pCurrentMaterial ?: continue

            val mat = AiMaterial()
            mat.name = pCurrentMaterial.materialName

            // convert illumination model
            mat.shadingModel = when (pCurrentMaterial.illumination_model) {
                0 -> AiShadingMode.noShading
                1 -> AiShadingMode.gouraud
                2 -> AiShadingMode.phong
                else -> {
                    System.err.println("OBJ: unexpected illumination model (0-2 recognized)")
                    AiShadingMode.gouraud
                }
            }

            // multiplying the specular exponent with 2 seems to yield better results
            pCurrentMaterial.shineness *= 4.f

            // Adding material colors
            mat.color = AiMaterialColor()
            mat.color?.ambient = pCurrentMaterial.ambient
            mat.color?.diffuse = pCurrentMaterial.diffuse
            mat.color?.specular = pCurrentMaterial.specular
            mat.color?.emissive = pCurrentMaterial.emissive
            mat.shininess = pCurrentMaterial.shineness
            mat.opacity = pCurrentMaterial.alpha

            // Adding refraction index
            mat.refracti = pCurrentMaterial.ior

            // Adding textures
            pCurrentMaterial.textures.firstOrNull { it.type == Material.Texture.Type.diffuse }?.let {

                mat.textures!!.add(AiMaterialTexture(type = AiTextureType.diffuse))
            }

            // TODO

            // Store material property info in material array in scene
            pScene.mMaterials.add(mat)
            pScene.mNumMaterials++
        }

        // Test number of created materials.
        assert(pScene.mNumMaterials == numMaterials)
    }
}
































