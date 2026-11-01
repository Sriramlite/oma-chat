import pandas as pd
import numpy as np
A=np.zeros(3,dtype=[('A','i8'),('B','f8')])
print(A)
B=pd.DataFrame(A)
print(B)
