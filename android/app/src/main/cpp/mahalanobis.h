#include <Eigen/Dense>
#include <cmath>

using namespace Eigen;

class MahalanobisNormalizer{
private:
    VectorXd meanVector;
    MatrixXd inverseCovariance;

public:
    void fit(const MatrixXd& X) {
        meanVector = X.colwise().mean();
        MatrixXd centered = X.rowwise() - meanVector.transpose();
        MatrixXd cov = (centered.adjoint() * centered) / (double(X.rows()) - 1.0);  // ✅ Also fixed parenthesis
        MatrixXd reg = MatrixXd::Identity(cov.rows(), cov.cols()) * 1e-6;
        inverseCovariance = (cov + reg).inverse();
    }

    double distance(const VectorXd& x) {
        VectorXd diff = x - meanVector;
        double dist_sq = diff.transpose() * inverseCovariance * diff;
        return std::sqrt(std::max(dist_sq, 0.0));
    }
};