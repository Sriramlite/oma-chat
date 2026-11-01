import numpy as np
arr=np.array([[1,2,3],[4,2,5]])
print("Array is of type :",type(arr))
print("No of dimentions :" , arr.ndim)
print("shape of array:",arr.shape)
print("size of array:",arr.size)
print("Array starts elements of type:",arr.dtype)

import numpy as np
a=np.array([[1,2,3],[3,4,5],[4,5,6]])
print(a)
print("After slicing")
print(a[1:])

a1=np.array([[1,2,3],[3,4,5],[4,5,6]])
print('our array is:')
print(a1)
print("The items in the second column are:")
print(a1[...,1])
print('\n')
print('THe items in column 1 on wards are :')
print(a1[...,1:])

                      
