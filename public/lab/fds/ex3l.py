import pandas as pd
import numpy as np
rng=np.random.RandomState(42)
ser=pd.Series(rng.randint(0,10,4))
print(ser)
df=pd.DataFrame(rng.randint(0,10,(3,4)),columns=['A','B','C','D'])
print(df)
A=np.exp(ser)
print(A)
B=np.sin(df*np.pi/4)
print(B)
