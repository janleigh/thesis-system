class OCSVMModel{
private:
    svm_model * model;

public:
    void train(std::vector<std::vector<double>>& scaled_vectors){
        svm_parameter param;
        param.svm_type = ONE_CLASS;
        param.kernel_type = RBF;
        param.nu = 0.1;
        param.gamma = 1.0;

        svm_problem prob;

        model = svm_train(&prob, &param);
    }

    double anomalyScore(const std::vector<double>& vector) {
        svm_node x[7];

        double dec_values[1];
        svm_predict_values(model, x, dec_values);

        return 0.0;
    }
    void saveModel(const char * filepath){
        svm_save_model(filepath, model);
    }
};