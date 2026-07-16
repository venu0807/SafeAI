import tensorflow as tf
import time

a = tf.random.normal([8000, 8000])
b = tf.random.normal([8000, 8000])

for _ in range(5):
    c = tf.matmul(a, b)
    time.sleep(1)
