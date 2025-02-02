package me.petrolingus.deconvolution;

import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.apache.commons.math3.analysis.function.Gaussian;

import java.util.Random;

public class Controller {

    public VBox chartBox;

    public TextField amplitude1;
    public TextField sigma1;
    public TextField mu1;

    public TextField amplitude2;
    public TextField sigma2;
    public TextField mu2;

    public TextField amplitude3;
    public TextField sigma3;
    public TextField mu3;

    public TextField impulseAmplitude;
    public TextField impulseSigma;

    public TextField samplesCountField;
    public TextField noisePercentField;
    public CheckBox noiseCheckBox;
    public TextField accuracyTextField;

    public Button createSignalButton;
    public Button createRandomSignalButton;
    public Button deconvolutionButton;
    public Button deconvolutionStopButton;

    public TextField vectorLengthField;
    public TextField functionalValueField;
    public TextField deviationField;
    public TextField counterField;

    public AreaChart<Number, Number> signalChart;
    public AreaChart<Number, Number> impulseChart;
    public AreaChart<Number, Number> convolutionChart;

    final MathLogic task = new MathLogic();

    public void initialize() {
        task.vectorLengthField = vectorLengthField;
        task.functionalValueField = functionalValueField;
        task.deviationField = deviationField;
        task.counterField = counterField;

        generateSignal();

        createSignalButton.disableProperty().bind(task.runningProperty());
        createRandomSignalButton.disableProperty().bind(task.runningProperty());
        deconvolutionButton.disableProperty().bind(task.runningProperty());
        deconvolutionStopButton.disableProperty().bind(task.runningProperty().not());
    }

    public void generateSignal() {

        // Получение числа количества отсчетов из поля в окне
        int n = Integer.parseInt(samplesCountField.getText());

        Gaussian gaussian1 = createGaussian(amplitude1, sigma1, mu1);
        Gaussian gaussian2 = createGaussian(amplitude2, sigma2, mu2);
        Gaussian gaussian3 = createGaussian(amplitude3, sigma3, mu3);

        // Создание входного сигнала
        XYChart.Series<Number, Number> signal = new XYChart.Series<>();
        for (int i = 0; i < n; i++) {
            double x = 1.0 * i;
            double y1 = gaussian1.value(x);
            double y2 = gaussian2.value(x);
            double y3 = gaussian3.value(x);
            signal.getData().add(new XYChart.Data<>(i, y1 + y2 + y3));
        }
        if (signalChart == null) {
            signalChart = createChart(signal);
            chartBox.getChildren().set(0, signalChart);
        } else {
            signalChart.getData().set(0, signal);
            if (signalChart.getData().size() == 2) {
                signalChart.getData().set(1, signalChart.getData().get(1));
            }
        }

        Gaussian impulseGaussian = createGaussian(impulseAmplitude, impulseSigma, new TextField("0"));

        // Создание импульсной характеристики
        XYChart.Series<Number, Number> impulse = new XYChart.Series<>();
        for (int i = 0; i < n; i++) {
            double x = (i < n / 2) ? 1.0 * i : 1.0 * (i - n);
            double y = impulseGaussian.value(x);
            impulse.getData().add(new XYChart.Data<>(i, y));
        }
        impulseChart = createChart(impulse);
        chartBox.getChildren().set(1, impulseChart);

        // Создание свертки входного сигнала {signal} с импульсной характеристикой {impulse}
        XYChart.Series<Number, Number> convolution = new XYChart.Series<>();
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                double a = signal.getData().get(j).getYValue().doubleValue();
                double b = impulse.getData().get((n - j + i) % n).getYValue().doubleValue();
                sum += a * b;
            }
            convolution.getData().add(new XYChart.Data<>(i, sum));
        }
        if (convolutionChart == null) {
            convolutionChart = createChart(convolution);
            chartBox.getChildren().set(2, convolutionChart);
        } else {
            convolutionChart.getData().set(0, convolution);
            if (convolutionChart.getData().size() == 2) {
                convolutionChart.getData().set(1, convolutionChart.getData().get(1));
            }
        }

        if (noiseCheckBox.isSelected()) {
            // Генерация шума
            Random random = new Random();
            double[] noise = new double[n];
            for (int i = 0; i < n; i++) {
                double y;
                do {
                    y = random.nextGaussian();
                } while (y < 0);
                noise[i] = y;
            }

            // Расчет энергии шума
            double noiseEnergy = 0;
            for (int i = 0; i < n; i++) {
                noiseEnergy += Math.pow(noise[i], 2);
            }

            // Расчет энергии свертки
            double convolutionEnergy = 0;
            for (int i = 0; i < n; i++) {
                convolutionEnergy += Math.pow(convolution.getData().get(i).getYValue().doubleValue(), 2);
            }

            double noiseCoefficient = Double.parseDouble(noisePercentField.getText());
            double alpha = Math.sqrt((noiseCoefficient / 100.0) * convolutionEnergy / noiseEnergy);

            XYChart.Series<Number, Number> convolutionWithNoise = new XYChart.Series<>();
            for (int i = 0; i < n; i++) {
                double value = convolution.getData().get(i).getYValue().doubleValue() + alpha * noise[i];
                convolutionWithNoise.getData().add(new XYChart.Data<>(i, value));
            }
            convolutionChart.getData().set(0, convolutionWithNoise);
        }
    }

    public void onGenerateRandomDeconvolution() {

        // Получение числа количества отсчетов из поля в окне
        int n = Integer.parseInt(samplesCountField.getText());

        // Генерация случайных множителей Лагранжа
        task.startPoint = new double[n];
        for (int i = 0; i < n; i++) {
            task.startPoint[i] = 2.0 * Math.random() - 1.0;
        }

        // Создание восстановленного сигнала на основе множителей Лагранжа {lambdas}
        XYChart.Series<Number, Number> recoveredSignal = new XYChart.Series<>();
        for (int i = 0; i < n; i++) {
            double value = -1;
            for (int j = 0; j < n; j++) {
                int id = (n - j + i) % n;
                double hi = impulseChart.getData().get(0).getData().get(id).getYValue().doubleValue();
                value -= task.startPoint[j] * hi;
            }
            recoveredSignal.getData().add(new XYChart.Data<>(i, Math.exp(value)));
        }
        if (signalChart.getData().size() == 1) {
            signalChart.getData().add(recoveredSignal);
        } else {
            signalChart.getData().set(1, recoveredSignal);
        }


        // Создание свертки востановленного сигнала {recoveredSignal} с импульсной характеристикой {impulse}
        XYChart.Series<Number, Number> recoveredConvolutionSignal = new XYChart.Series<>();
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < n; j++) {
                double a = recoveredSignal.getData().get(j).getYValue().doubleValue();
                double b = impulseChart.getData().get(0).getData().get((n - j + i) % n).getYValue().doubleValue();
                sum += a * b;
            }
            recoveredConvolutionSignal.getData().add(new XYChart.Data<>(i, sum));
        }
        if (convolutionChart.getData().size() == 1) {
            convolutionChart.getData().add(recoveredConvolutionSignal);
        } else {
            convolutionChart.getData().set(1, recoveredConvolutionSignal);
        }

        // Рассчет суммы квадратов разностей двух сверток
        task.startDeviation = 0;
        for (int i = 0; i < n; i++) {
            double a = convolutionChart.getData().get(0).getData().get(i).getYValue().doubleValue();
            double b = recoveredConvolutionSignal.getData().get(i).getYValue().doubleValue();
            task.startDeviation += (a - b) * (a - b);
        }
        functionalValueField.setText(String.valueOf(task.startDeviation));
    }

    public void onDeconvolution() {
        onGenerateRandomDeconvolution();
        if (!task.isRunning) {
            task.accuracy = Double.parseDouble(accuracyTextField.getText());
            task.signalChart = signalChart;
            task.convolutionChart = convolutionChart;
            fillDataArrays();
            task.reset();
            task.start();
            task.isRunning = !task.isRunning;
        }
    }

    private void fillDataArrays() {

        int n = Integer.parseInt(samplesCountField.getText());

        task.impulseData = new double[n];
        for (int i = 0; i < n; i++) {
            task.impulseData[i] = impulseChart.getData().get(0).getData().get(i).getYValue().doubleValue();
        }

        task.convolutionData = new double[n];
        for (int i = 0; i < n; i++) {
            task.convolutionData[i] = convolutionChart.getData().get(0).getData().get(i).getYValue().doubleValue();
        }

        task.recoveredData = new double[n];
        for (int i = 0; i < n; i++) {
            task.recoveredData[i] = signalChart.getData().get(1).getData().get(i).getYValue().doubleValue();
        }

        task.recoveredConvolutionData = new double[n];
        for (int i = 0; i < n; i++) {
            task.recoveredConvolutionData[i] = convolutionChart.getData().get(1).getData().get(i).getYValue().doubleValue();
        }
    }

    private AreaChart<Number, Number> createChart(XYChart.Series<Number, Number> series) {
        int tickUnit = 5;
        NumberAxis xAxis = new NumberAxis(0, series.getData().size() - 1, tickUnit);
        NumberAxis yAxis = new NumberAxis();
        AreaChart<Number, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        chart.getData().clear();
        chart.getData().add(series);
        return chart;
    }

    public void onPressRandomValues() {
        int n = Integer.parseInt(samplesCountField.getText());
        GaussianDome.getRandom(amplitude1, sigma1, mu1, n);
        GaussianDome.getRandom(amplitude2, sigma2, mu2, n);
        GaussianDome.getRandom(amplitude2, sigma3, mu3, n);
        generateSignal();
    }

    public void onDeconvolutionStop() {
        task.cancel();
        task.reset();
    }

    private Gaussian createGaussian(TextField amplitude, TextField sigma, TextField mu) {
        double a = Double.parseDouble(amplitude.getText());
        double s = Double.parseDouble(sigma.getText());
        double m = Double.parseDouble(mu.getText());
        return new Gaussian(a, m, s);
    }

}
