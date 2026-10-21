# Manual de operación: Hadoop multinodo y single node

Laboratorio CC531 · Emergencias en la Red Vial Nacional (MTC) · 19/10/2026

Este manual reúne los procedimientos para trabajar con los dos modos en las VM existentes. Hadoop y Java ya deben estar instalados. No necesitas reinstalarlos ni crear otra VM para cambiar de modo.

## 1. Entorno y ubicaciones

| Equipo | IP | Usuario | Función |
|---|---|---|---|
| Pop!_OS, equipo físico | La de tu red habitual | `janiopi` | VirtualBox, Codex, edición, Maven y almacenamiento de entregables |
| VM `master` | `192.168.56.10` | `master` | Administración del clúster; todos los servicios cuando se usa single |
| VM `worker1` | `192.168.56.11` | `worker1` | DataNode y NodeManager en multinodo |
| VM `worker2` | `192.168.56.12` | `worker2` | DataNode y NodeManager en multinodo |

Versiones de referencia: **Hadoop 3.4.2** y **OpenJDK 11**. Instalación común: `/opt/hadoop`.

| Elemento | Multinodo (`cluster`) | Un nodo (`single`) |
|---|---|---|
| Configuración | `/opt/hadoop/etc/hadoop` | `/opt/hadoop/conf-single` |
| Datos y temporales locales | `/opt/hadoop/runtime/cluster` | `/opt/hadoop/runtime/single` |
| Logs de servicios | `/opt/hadoop/runtime/cluster/logs` | `/opt/hadoop/runtime/single/logs` |
| PID de servicios | `/opt/hadoop/runtime/cluster/pids` | `/opt/hadoop/runtime/single/pids` |
| VM encendidas durante las mediciones | Las tres | Solo master |
| DataNodes esperados | worker1 y worker2 | master |
| NodeManagers esperados | worker1 y worker2 | master |
| Replicación HDFS | 2 | 1 |
| Dirección de HDFS | `hdfs://master:9000` | `hdfs://master:9000` |

Los modos usan los mismos puertos, pero **no deben ejecutarse simultáneamente**. Sus HDFS son independientes: un archivo cargado en uno no aparece automáticamente en el otro.

## 2. Reglas de trabajo

1. **Selecciona el perfil en cada terminal nueva de master.** La selección se hereda a sus procesos hijos, no a otras terminales.
2. **Detén los servicios con el perfil que los arrancó antes de seleccionar el otro.** Cambiar una variable no detiene ni cambia los procesos existentes.
3. **No vuelvas a formatear un HDFS inicializado.** Formatear es una operación inicial, no una forma de arrancar Hadoop.
4. Arranca y ejecuta Hadoop como usuario `master`, sin `sudo`. Cada trabajador ejecuta sus servicios con su propia cuenta.
5. Cada ejecución debe escribir en una salida HDFS nueva. Los ejemplos de rutas no autorizan borrar resultados anteriores.
6. Si has cambiado la configuración desde la instalación, verifica que los valores de este manual coincidan con los reales.

### Qué modo elegir

| Situación | Modo y motivo |
|---|---|
| Probar una primera versión del driver o revisar resultados pequeños | Single: permite probar HDFS/YARN con menor consumo de RAM de la laptop. |
| Revisar distribución de tareas, comunicación y uso de ambos trabajadores | Multinodo: permite observar los dos NodeManagers y sus tareas. |
| Preparar la comparación solicitada en el parcial | Ambos, por separado, con el mismo trabajo, datos y parámetros. |
| Corregir código o compilar | Pop!_OS; no necesitas arrancar Hadoop para compilar. |

El modo single simplifica las pruebas, pero la validación final debe incluir multinodo. Que una consulta funcione en un solo nodo no garantiza que funcione correctamente al distribuirse.

## 3. Entrar y seleccionar perfil

### Desde Pop!_OS

```bash
ssh master@192.168.56.10
```

### Dentro de master: comprobar identidad

```bash
whoami
hostname
```

Ambos deben mostrar `master`.

### Seleccionar multinodo

```bash
export HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop
```

### Seleccionar single

```bash
export HADOOP_CONF_DIR=/opt/hadoop/conf-single
```

### Verificar la selección antes de arrancar o lanzar consultas

```bash
echo "$HADOOP_CONF_DIR"
hdfs getconf -confKey dfs.namenode.name.dir
hdfs getconf -confKey dfs.datanode.data.dir
hdfs getconf -confKey dfs.replication
```

En multinodo deben aparecer rutas `/runtime/cluster/` y replicación 2; en single, `/runtime/single/` y replicación 1. **Estas comprobaciones muestran la configuración del cliente; no demuestran por sí mismas qué procesos están ejecutándose.** Comprueba además los nodos registrados y los procesos.

Si ejecutas `source ~/.bashrc`, revisa de nuevo la selección: nuestra configuración original de ese archivo define el perfil multinodo.

## 4. Arrancar multinodo desde los servicios detenidos

**En VirtualBox:** enciende master, worker1 y worker2. Espera a que arranque Ubuntu. Conserva las redes NAT y Host-only configuradas.

**Dentro de master:**

```bash
export HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop
ssh -o BatchMode=yes worker1 'hostname'
ssh -o BatchMode=yes worker2 'hostname'
hdfs getconf -confKey dfs.namenode.name.dir
```

Si SSH funciona y la ruta es la del clúster:

```bash
start-dfs.sh
start-yarn.sh
mapred --daemon start historyserver
```

Verifica:

```bash
jps
ssh worker1 '/usr/lib/jvm/java-11-openjdk-amd64/bin/jps'
ssh worker2 '/usr/lib/jvm/java-11-openjdk-amd64/bin/jps'
hdfs dfsadmin -report
yarn node -list
```

| Lugar | Procesos esperados, además de Jps |
|---|---|
| master | NameNode, SecondaryNameNode, ResourceManager, JobHistoryServer |
| worker1 | DataNode, NodeManager |
| worker2 | DataNode, NodeManager |

HDFS debe registrar **dos DataNodes vivos** y YARN **dos NodeManagers RUNNING**.

Los alias SSH de master deben seleccionar User worker1 para Host worker1 y User worker2 para Host worker2. Así se conserva la configuración de usuarios distintos.

## 5. Detener multinodo

Termina primero las consultas. Si deseas comprobar aplicaciones YARN que aún no han finalizado:

```bash
yarn application -list
```

Dentro de master, con el perfil del clúster:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop
mapred --daemon stop historyserver
stop-yarn.sh
stop-dfs.sh
jps
ssh worker1 '/usr/lib/jvm/java-11-openjdk-amd64/bin/jps'
ssh worker2 '/usr/lib/jvm/java-11-openjdk-amd64/bin/jps'
```

Los servicios Hadoop deben desaparecer. Si queda alguno, revisa sus logs antes de seguir. No cambies de perfil mientras permanezcan servicios del anterior.

Para apagar los trabajadores, ejecuta en la sesión SSH o consola de **cada trabajador**:

```bash
sudo poweroff
```

Esto cerrará su conexión SSH. Para apagar toda la instalación, apaga master al final con el mismo comando, después de detener sus servicios.

## 6. Arrancar single desde los servicios detenidos

**En VirtualBox:** deja encendida solo master. El perfil `/opt/hadoop/conf-single` debe existir e identificar a master como único trabajador. Si aún no lo has creado, consulta el apartado 12.

Dentro de master:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/conf-single
ssh -o BatchMode=yes master 'hostname'
hdfs getconf -confKey dfs.namenode.name.dir
hdfs getconf -confKey dfs.replication
```

Comprueba `/runtime/single/name` y replicación 1. Arranca:

```bash
start-dfs.sh
start-yarn.sh
mapred --daemon start historyserver
```

Verifica:

```bash
jps
hdfs dfsadmin -report
yarn node -list
```

En master deben aparecer NameNode, DataNode, SecondaryNameNode, ResourceManager, NodeManager y JobHistoryServer. HDFS debe mostrar **un DataNode vivo** y YARN **un NodeManager RUNNING**, ambos de master.

Mantén `mapreduce.framework.name=yarn`. En single pueden ejecutarse varios mappers/reducers, todos en la misma VM.

## 7. Detener single y alternar entre modos

### Detener single

Dentro de master:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/conf-single
mapred --daemon stop historyserver
stop-yarn.sh
stop-dfs.sh
jps
```

### Secuencia para cambiar

| Cambio | Orden |
|---|---|
| Multinodo → single | Terminar consultas → detener multinodo con su perfil → verificar procesos → apagar trabajadores → seleccionar single → arrancar single → comprobar un trabajador |
| Single → multinodo | Terminar consultas → detener single con su perfil → verificar procesos → encender trabajadores → seleccionar multinodo → arrancar multinodo → comprobar dos trabajadores |

No se formatea ninguno de los HDFS durante estos cambios. Al volver a un modo, sus entradas y salidas previas permanecen disponibles.

## 8. Desarrollo, compilación y ejecución de consultas

### En Pop!_OS: desarrollar y compilar

Trabaja con Codex o IntelliJ en tu proyecto Java/Maven. Usa dependencias Hadoop 3.4.2 con alcance `provided` y bytecode compatible con Java 11. Los nombres de proyecto, JAR, clase y dataset siguientes son **ejemplos propuestos**; sustitúyelos por los de tu implementación.

```bash
cd ~/IdeaProjects/laboratorio-mtc
mvn clean package
```

Prepara una vez los directorios locales de master:

```bash
ssh master@192.168.56.10 'mkdir -p /home/master/laboratorio-mtc/jars /home/master/laboratorio-mtc/datos'
```

Copia el JAR desde Pop!_OS:

```bash
scp target/laboratorio-mtc-1.0-SNAPSHOT.jar \
  master@192.168.56.10:/home/master/laboratorio-mtc/jars/
```

No necesitas copiar manualmente el JAR a los trabajadores. El cliente Hadoop prepara su distribución para las tareas. Las dependencias adicionales a Hadoop deben empaquetarse o distribuirse explícitamente.

### En master: incorporar datos al HDFS activo

Primero copia el archivo procesado desde Pop!_OS, cuando exista:

```bash
scp datos/procesados/emergencias.tsv \
  master@192.168.56.10:/home/master/laboratorio-mtc/datos/
```

Después, **dentro de master**, selecciona el perfil previsto y sube la entrada:

```bash
hdfs dfs -mkdir -p /user/master/mtc/entrada
hdfs dfs -put /home/master/laboratorio-mtc/datos/emergencias.tsv /user/master/mtc/entrada/
hdfs dfs -ls /user/master/mtc/entrada
```

Repite la carga al preparar por primera vez el otro HDFS. Para comparar, ambos deben contener la misma versión de la entrada. No vuelvas a cargarla en cada modificación del código. Si ya existe, inspecciona su versión antes de reemplazarla.

### En master: ejecutar

Ejemplo multinodo, con clúster activo:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop
hadoop jar \
  /home/master/laboratorio-mtc/jars/laboratorio-mtc-1.0-SNAPSHOT.jar \
  pe.edu.uni.cc531.mtc.Consulta01Driver \
  /user/master/mtc/entrada/emergencias.tsv \
  /user/master/mtc/salidas/c1_cluster_run01
```

Ejemplo single, después de cambiar los servicios al modo single:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/conf-single
hadoop jar \
  /home/master/laboratorio-mtc/jars/laboratorio-mtc-1.0-SNAPSHOT.jar \
  pe.edu.uni.cc531.mtc.Consulta01Driver \
  /user/master/mtc/entrada/emergencias.tsv \
  /user/master/mtc/salidas/c1_single_run01
```

Seleccionar la variable sin cambiar los servicios no convierte un clúster en single. Comprueba los nodos antes de ejecutar.

Para C1 y C2, el driver debe encadenar sus tres jobs y esperar a que finalice cada etapa. Si alguna falla, debe devolver un código de salida de error. El código no debe forzar `file:///` ni `mapreduce.framework.name=local` al realizar estas pruebas.

### Leer y descargar resultados

Suponiendo que el driver escribe el resultado final en una subcarpeta `final`:

```bash
hdfs dfs -ls /user/master/mtc/salidas/c1_cluster_run01
hdfs dfs -cat '/user/master/mtc/salidas/c1_cluster_run01/final/part-r-*'
hdfs dfs -getmerge \
  /user/master/mtc/salidas/c1_cluster_run01/final \
  /home/master/laboratorio-mtc/datos/c1_cluster_run01.tsv
```

Desde Pop!_OS:

```bash
scp master@192.168.56.10:/home/master/laboratorio-mtc/datos/c1_cluster_run01.tsv .
```

Adapta la subcarpeta a la salida real del driver. Para Power BI prepara un archivo con encabezados, tipos y separadores documentados; `getmerge` concatena archivos, no agrega encabezados ni transforma su formato.

## 9. Medición y comparación

Usa `medir_hadoop.py` del paquete `metricas_hadoop.zip` entregado junto con el material de mediciones. Este manual no contiene ese script. Su ubicación propuesta en master es `/home/master/metricas-hadoop/medir_hadoop.py`.

Antes de medir:

- Comprueba que el número y los nombres de los trabajadores corresponden al modo.
- Prepara entradas y JAR antes del cronómetro.
- Conserva los mismos datos, JAR, parámetros, semillas, particiones de ML, splits, reducers y límites por tarea/NodeManager.
- Mide los cinco trabajos: C1, C2, DBSCAN, MLP_C y MLP_R. Incluye todas sus etapas; fija el mismo alcance de entrenamiento y evaluación.
- Haz un calentamiento no contabilizado y tres repeticiones medidas por trabajo y modo como protocolo recomendado.
- Usa salidas nuevas y no ejecutes cargas importantes adicionales en la laptop.

### Ejemplo de medición en multinodo

Dentro de master, después de arrancar correctamente el clúster:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop
python3 /home/master/metricas-hadoop/medir_hadoop.py medir \
  --modo cluster --trabajo C1 --repeticion 1 \
  --base /home/master/mediciones-mtc-v1 \
  -- /opt/hadoop/bin/hadoop jar \
  /home/master/laboratorio-mtc/jars/laboratorio-mtc-1.0-SNAPSHOT.jar \
  pe.edu.uni.cc531.mtc.Consulta01Driver \
  /user/master/mtc/entrada/emergencias.tsv \
  /user/master/mtc/salidas/c1_cluster_run01
```

### Ejemplo de medición en single

Después de cambiar los servicios a single:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/conf-single
python3 /home/master/metricas-hadoop/medir_hadoop.py medir \
  --modo single --trabajo C1 --repeticion 1 \
  --base /home/master/mediciones-mtc-v1 \
  -- /opt/hadoop/bin/hadoop jar \
  /home/master/laboratorio-mtc/jars/laboratorio-mtc-1.0-SNAPSHOT.jar \
  pe.edu.uni.cc531.mtc.Consulta01Driver \
  /user/master/mtc/entrada/emergencias.tsv \
  /user/master/mtc/salidas/c1_single_run01
```

**`--modo` selecciona los nodos que se monitorean; no cambia el perfil Hadoop ni arranca/detiene servicios.** La carpeta base y la etiqueta C1 son iguales para emparejar los dos modos. Cambia la repetición y salida en las siguientes corridas. Usa otra carpeta base si cambias el protocolo o dataset.

### Qué archivos consultar

| Archivo | Contenido |
|---|---|
| `driver.log` | Progreso, IDs de aplicaciones, salida y errores |
| `metadata.json` | Comando, perfil de medición, tiempos, estado y hash del JAR accesible |
| `*_cpu.csv` | CPU por núcleo y total normalizado del nodo |
| `*_memoria.csv` | RAM por nodo en MiB |
| `resumen_nodos.csv` | Promedios de CPU/RAM por nodo |
| `resumen_corrida.csv` | Tiempo y recursos agregados de la corrida exitosa |

CPU activa excluye idle, iowait y steal; RAM utilizada se define como `(MemTotal - MemAvailable) / 1024` MiB. La CPU del conjunto se pondera por vCPU; la RAM conjunta suma las medias de las VM. Se incluye master en multinodo. Los recursos incluyen el sistema operativo y servicios de cada VM. Consulta el LEEME del recolector para los detalles de muestreo y su pequeño margen de coordinación.

### Generar la comparativa

```bash
python3 /home/master/metricas-hadoop/medir_hadoop.py comparar \
  --base /home/master/mediciones-mtc-v1
```

Genera `comparativa.csv` y `resumen_por_modo.csv`. Las corridas fallidas se excluyen, conservando sus diagnósticos. La fórmula es:

**Speedup = mediana del tiempo single / mediana del tiempo cluster.**

Un valor menor que 1 significa que multinodo tardó más. Puede ocurrir con pocos datos, múltiples arranques de jobs, comunicaciones y contención del hardware compartido. No alteres ni dupliques datos para mejorar el resultado.

Las métricas accuracy/F1, MAE/RMSE/R² y Silhouette deben calcularlas tus algoritmos sobre los mismos datos y protocolo. El recolector de recursos no las obtiene automáticamente.

## 10. Interfaces, logs y evidencias

Las direcciones son las mismas en ambos modos, porque se reutiliza master:

| Servicio | Dirección desde Pop!_OS |
|---|---|
| HDFS | http://192.168.56.10:9870 |
| YARN | http://192.168.56.10:8088 |
| JobHistory | http://192.168.56.10:19888 |

Si un enlace utiliza nombres de nodos, conserva estas entradas en `/etc/hosts` de Pop!_OS y de las tres VM:

```text
192.168.56.10 master
192.168.56.11 worker1
192.168.56.12 worker2
```

### Monitorear visualmente

En una terminal de cada VM activa:

```bash
htop
```

Para multinodo puedes disponer tres terminales SSH visibles en Pop!_OS. Identifica el nodo y rotula la captura con consulta, modo, repetición y fecha/hora. Los promedios del informe se calculan desde las muestras guardadas, no desde una captura.

### Revisar logs de servicios

Multinodo, dentro de master:

```bash
ls -lt /opt/hadoop/runtime/cluster/logs
```

Single:

```bash
ls -lt /opt/hadoop/runtime/single/logs
```

Identifica el archivo `.log` del servicio que falla y lee sus últimas líneas:

```bash
tail -n 100 /ruta/al/archivo.log
```

En multinodo, revisa también los logs locales de los trabajadores cuando falle su DataNode o NodeManager. Para una aplicación YARN, sustituye el ID por el real:

```bash
yarn logs -applicationId application_1234567890000_0001
```

Los logs agregados pueden estar disponibles después de terminar la aplicación. Si no aparecen, consulta el NodeManager y el log local correspondiente.

### Evidencias para el informe y la exposición

- HDFS y YARN con un trabajador en single y dos en multinodo.
- Recursos durante cada tipo de consulta y modo: CPU por núcleo, RAM y procesos Java.
- Tareas/intentos en JobHistory que identifiquen el trabajador y el intervalo de ejecución. Para demostrar paralelismo, busca intervalos solapados en worker1 y worker2.
- Ejecución exitosa y salidas verificadas.
- Tabla y gráficos calculados con los CSV reales; métricas de ML/agrupamiento en tablas separadas.

En el informe conserva el detalle y usa anexos si es necesario. En la presentación selecciona evidencias legibles que quepan en el tiempo de exposición.

## 11. Problemas frecuentes

| Síntoma | Qué revisar |
|---|---|
| El HDFS parece vacío al cambiar de modo | Es otro almacenamiento. Comprueba el perfil y carga allí la entrada. |
| Single muestra dos NodeManagers | Siguen activos los trabajadores o no cambiaste realmente los servicios. Detén correctamente el modo anterior. |
| `Permission denied (publickey)` | Prueba SSH desde master; revisa alias, User, IdentityFile y la clave autorizada. |
| `FileAlreadyExistsException` en la salida | Usa una ruta nueva; conserva resultados anteriores. |
| `Connection refused` al NameNode | Verifica perfil, procesos, resolución de master y logs. |
| Un proceso aparece en jps pero no registra un nodo | Revisa logs, IP/nombres, puertos y permisos. jps solo acredita que existe un proceso Java. |
| `Incompatible clusterIDs` | Revisa si mezclaste rutas single/cluster o formateaste un NameNode existente. Conserva archivos y logs; no borres directorios como primera solución. |
| NodeManager UNHEALTHY | Revisa disco libre dentro de la VM, directorios locales, permisos y diagnósticos de YARN. |
| Un trabajo queda esperando | Revisa nodos RUNNING y memoria/vcores disponibles. El ApplicationMaster también necesita recursos. |
| `ClassNotFoundException` | Comprueba el nombre completo de la clase, contenido del JAR y dependencias. |
| Advertencia de biblioteca nativa | Comprueba si el trabajo continúa correctamente; no equivale por sí sola a fallo. |
| `Cannot set priority` | Comprueba si el proceso quedó activo y consulta su log; el mensaje por sí solo no determina el estado final. |
| El navegador no resuelve worker1 | Añade los nombres del laboratorio a `/etc/hosts` de Pop!_OS. |

No desactives comprobaciones de memoria/disco para ocultar errores: identifica primero su causa.

## 12. Preparación inicial del perfil single, solo si todavía no existe

Omite esta sección si ya verificaste el perfil single. Antes de prepararlo, detén multinodo siguiendo el apartado 5.

Dentro de master, si `/opt/hadoop/conf-single` no existe:

```bash
cp -a /opt/hadoop/etc/hadoop /opt/hadoop/conf-single
sed -i \
  's@/opt/hadoop/runtime/cluster@/opt/hadoop/runtime/single@g' \
  /opt/hadoop/conf-single/core-site.xml \
  /opt/hadoop/conf-single/hdfs-site.xml \
  /opt/hadoop/conf-single/yarn-site.xml \
  /opt/hadoop/conf-single/hadoop-env.sh
mkdir -p /opt/hadoop/runtime/single/{name,data,tmp,logs,pids,yarn-local,yarn-logs}
```

Edita `/opt/hadoop/conf-single/hdfs-site.xml` para dejar `dfs.replication` en 1. Edita `/opt/hadoop/conf-single/workers` y deja solo `master`. Conserva los límites por tarea y por NodeManager y la ejecución mediante YARN.

Activa y comprueba las rutas:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/conf-single
hdfs getconf -confKey dfs.namenode.name.dir
hdfs getconf -confKey dfs.datanode.data.dir
hdfs getconf -confKey dfs.replication
ls -la /opt/hadoop/runtime/single/name
```

**Únicamente si se trata de este HDFS nuevo, la ruta verificada es `/runtime/single/name` y el directorio está vacío**, inicialízalo una vez:

```bash
hdfs --config /opt/hadoop/conf-single namenode -format
```

Si existe `current`, no formatees. Después continúa con el apartado 6 para arrancar. Este procedimiento no modifica los datos del clúster.

## 13. Comprobación antes de cada benchmark

- [ ] Estoy conectado a la VM master como master.
- [ ] Detuve el modo anterior antes de cambiar.
- [ ] `HADOOP_CONF_DIR` señala el perfil correcto en esta terminal.
- [ ] HDFS y YARN tienen los nodos previstos y saludables.
- [ ] En single, worker1 y worker2 están apagados.
- [ ] Entrada, JAR, parámetros y protocolo coinciden con la comparación.
- [ ] La salida HDFS de esta corrida no existe.
- [ ] El recolector usa el mismo modo que los servicios activos.
- [ ] Guardé logs, CSV, evidencias y verifiqué los resultados.

## Referencias

- [Apache Hadoop 3.4.2: configuración del clúster](https://hadoop.apache.org/docs/r3.4.2/hadoop-project-dist/hadoop-common/ClusterSetup.html)
- [Apache Hadoop 3.4.2: un nodo y YARN](https://hadoop.apache.org/docs/r3.4.2/hadoop-project-dist/hadoop-common/SingleCluster.html)
- [Tutorial MapReduce](https://hadoop.apache.org/docs/r3.4.2/hadoop-mapreduce-client/hadoop-mapreduce-client-core/MapReduceTutorial.html)
- [Comandos del sistema de archivos](https://hadoop.apache.org/docs/r3.4.2/hadoop-project-dist/hadoop-common/FileSystemShell.html)

Los comandos se han revisado contra la configuración acordada en la conversación. La operación real y el rendimiento deben verificarse en tus VM; no se incluyen resultados experimentales inventados.
