#include <jni.h>
#include <vector>
#include <cmath>
#include <map>
#include <numeric>
#include <algorithm>
#include "svm.h"
#include "mahalanobis.h"
#include "ocsvm_model.h"
#include <Eigen/Dense>

using namespace Eigen;

// Global model instances
static MahalanobisNormalizer mahal_model;
static OCSVMModel ocsvm_model;
static bool models_trained = false;

// Helper function to calculate mean
double calculateMean(const std::vector<double>& data) {
    if (data.empty()) return 0.0;
    double sum = std::accumulate(data.begin(), data.end(), 0.0);
    return sum / data.size();
}

// Helper function to calculate standard deviation
double calculateStdDev(const std::vector<double>& data, double mean) {
    if (data.empty()) return 0.0;
    double sq_sum = 0.0;
    for (double val : data) {
        sq_sum += (val - mean) * (val - mean);
    }
    return std::sqrt(sq_sum / data.size());
}


extern "C" JNIEXPORT jdouble JNICALL
Java_com_chate_thesis_KeystrokeIMEService_processKeystrokeWindow(
        JNIEnv *env,
        jobject /* this */,
        jdoubleArray timingsArray) {

    jsize length = env->GetArrayLength(timingsArray);
    jdouble *timings = env->GetDoubleArrayElements(timingsArray, nullptr);


    struct KeystrokeEvent {
        double keyCode;
        double eventType;
        double timestamp;
    };

    std::vector<KeystrokeEvent> events;
    for (int i = 0; i < length; i += 3) {
        if (i + 2 < length) {
            events.push_back({timings[i], timings[i+1], timings[i+2]});
        }
    }


    env->ReleaseDoubleArrayElements(timingsArray, timings, 0);

    // Extract timing metrics
    std::vector<double> dwell_times;
    std::vector<double> flight_times;
    std::vector<double> iki_times;

    std::vector<double> down_timestamps;
    std::vector<double> up_timestamps;
    std::map<double, double> last_down_time;  // ← Now works with #include <map>


    for (const auto& e : events) {
        if (e.eventType == 0.0) {  // Key Down
            last_down_time[e.keyCode] = e.timestamp;
            down_timestamps.push_back(e.timestamp);
        } else if (e.eventType == 1.0) {  // Key Up
            if (last_down_time.find(e.keyCode) != last_down_time.end()) {
                double dwell = e.timestamp - last_down_time[e.keyCode];
                if (dwell >= 0) {
                    dwell_times.push_back(dwell);
                }
                last_down_time.erase(e.keyCode);
            }
            up_timestamps.push_back(e.timestamp);
        }
    }

    // Calculate IKI (Inter-Key Interval) - time between consecutive key downs
    for (size_t i = 1; i < down_timestamps.size(); ++i) {
        iki_times.push_back(down_timestamps[i] - down_timestamps[i-1]);
    }

    // Calculate Flight Time - time between key up and next key down
    size_t pairs = std::min(up_timestamps.size(), down_timestamps.size() - 1);
    for (size_t i = 0; i < pairs; ++i) {
        flight_times.push_back(down_timestamps[i+1] - up_timestamps[i]);
    }

    // Compute 6D Statistical Vector
    double dwell_mean = calculateMean(dwell_times);
    double dwell_std = calculateStdDev(dwell_times, dwell_mean);
    double flight_mean = calculateMean(flight_times);
    double flight_std = calculateStdDev(flight_times, flight_mean);
    double iki_mean = calculateMean(iki_times);
    double iki_std = calculateStdDev(iki_times, iki_mean);

    std::vector<double> stat_vector = {
            dwell_mean, dwell_std,
            flight_mean, flight_std,
            iki_mean, iki_std
    };

    // Convert to Eigen vector
    Eigen::VectorXd vec(6);
    for (int i = 0; i < 6; ++i) {
        vec(i) = stat_vector[i];
    }

    // Layer 3: Score with Mahalanobis Distance
    double mahal_score = 0.5;
    if (models_trained) {
        double raw_mahal = mahal_model.distance(vec);
        mahal_score = std::min(raw_mahal / 50.0, 1.0);
    }

    // Layer 3: Score with OCSVM
    double ocsvm_score = 0.5;
    if (models_trained) {
        ocsvm_score = ocsvm_model.anomalyScore(stat_vector);
    }

    // Layer 4: Score Fusion (Chapter 3 formula)

    double composite_score = (0.6 * ocsvm_score) + (0.4 * mahal_score);

    // Clamp score to [0, 1] range
    composite_score = std::max(0.0, std::min(1.0, composite_score));

    return composite_score;
}

// JNI Function for Training (to be called during enrollment)
extern "C" JNIEXPORT jboolean JNICALL
Java_com_chate_thesis_KeystrokeIMEService_nativeTrainModels(
        JNIEnv *env,
        jobject /* this */,
        jdoubleArray enrollmentData,
        jint numVectors) {

    if (numVectors < 2) return false;

    jdouble *data = env->GetDoubleArrayElements(enrollmentData, nullptr);

    // Parse enrollment data into Eigen matrix
    Eigen::MatrixXd X(numVectors, 6);
    std::vector<std::vector<double>> vectors;

    for (int i = 0; i < numVectors; i++) {
        std::vector<double> vec(6);
        for (int j = 0; j < 6; j++) {
            double val = data[i * 6 + j];
            vec[j] = val;
            X(i, j) = val;
        }
        vectors.push_back(vec);
    }

    env->ReleaseDoubleArrayElements(enrollmentData, data, 0);

    // Train Mahalanobis Model
    mahal_model.fit(X);

    // Train OCSVM Model
    ocsvm_model.train(vectors);

    models_trained = true;
    return true;
}