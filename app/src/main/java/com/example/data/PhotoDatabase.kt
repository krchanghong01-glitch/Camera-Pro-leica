package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "captured_photos")
data class CapturedPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sceneName: String, // e.g., "Wetzlar Leica Store", "Munich Rain", etc.
    val cameraMode: String, // PHOTO, PRO, NIGHT, PORTRAIT, VIDEO
    val leicaLook: String, // Leica Authentic, Leica Vibrant, Leica BW Classic, etc.
    val iso: Int,
    val shutterSpeed: String,
    val whiteBalance: Int, // Kelvin
    val exposureValue: Float,
    val focalLength: String, // "28mm", "35mm", "50mm"
    val manualFocusValue: Int, // 0 - 100
    val aiSceneDetected: String?, // "Street", "Portrait", "Sky"
    val aiFeaturesActive: String, // comma-separated list
    val isVideo: Boolean = false,
    val videoDurationMs: Long = 0L,
    val customTitle: String? = null
)

@Dao
interface CapturedPhotoDao {
    @Query("SELECT * FROM captured_photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<CapturedPhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: CapturedPhoto)

    @Delete
    suspend fun deletePhoto(photo: CapturedPhoto)

    @Query("DELETE FROM captured_photos")
    suspend fun clearAll()
}

@Database(entities = [CapturedPhoto::class], version = 1, exportSchema = false)
abstract class PhotoDatabase : RoomDatabase() {
    abstract val dao: CapturedPhotoDao

    companion object {
        @Volatile
        private var INSTANCE: PhotoDatabase? = null

        fun getDatabase(context: Context): PhotoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotoDatabase::class.java,
                    "leica_camera_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class PhotoRepository(private val dao: CapturedPhotoDao) {
    val allPhotos: Flow<List<CapturedPhoto>> = dao.getAllPhotos()

    suspend fun insertPhoto(photo: CapturedPhoto) {
        dao.insertPhoto(photo)
    }

    suspend fun deletePhoto(photo: CapturedPhoto) {
        dao.deletePhoto(photo)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
