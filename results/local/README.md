# Resultados verificados en Hadoop local

Fuente: CSV incluido en la raíz del proyecto. Hadoop 3.3.0, `fs.defaultFS=file:///`, `mapreduce.framework.name=local`; Java 11 para ejecutar. Estos resultados **no son una medición multinodo**.

| Consulta | Resultado |
|---|---|
| Duración | Primer lugar TUMBES: 56.8000 días en 5 casos. |
| Interrupción | Primer lugar PE-5NB: 100.0000 % en 11 casos. |
| DBSCAN | 64 grupos, 1162 puntos núcleo, 63 de borde, 137 de ruido; Davies-Bouldin 0.446716. |
| MLP clasificación | 40 épocas, prueba: accuracy 0.593284, F1 0.384181, pérdida no ponderada 0.683213. |
| MLP regresión | 40 épocas, prueba: MAE 13.518356 días, RMSE 24.372828 días, pérdida transformada 0.027163. |

Las salidas principales se conservan en subdirectorios vecinos. `powerbi/emergencias_clusters.csv` contiene los 1362 puntos con sus grupos. El verificador `scripts/verify_outputs.py` comprobó las tablas y los grupos contra cálculos independientes, además del descenso de las pérdidas de entrenamiento.

Esta ejecución local no mide tiempos, CPU, memoria ni speedup comparables. Las mediciones posteriores de uno y varios nodos se documentan en [RESULTADOS_BENCHMARK.md](../../RESULTADOS_BENCHMARK.md).
