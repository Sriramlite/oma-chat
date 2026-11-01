import numpy as np
a=np.array([1,2,3])
b=np.array([[4,5,6],[7,8,9]])
zeros=np.zeros((2,3))
ones=np.ones((2,3))
arrange=np.arange(0,10,2)
linspace=np.linspace(0,1,5)
x=np.array([1,2,3])
y=np.array([4,5,6])
add=x+y
sub=x-y
mul=x*y
div=y/x
power=x**2
neg=-x
sqrt=np.sqrt(x)
data=np.array([[1,2],[3,4]])
total_sum=np.sum(data)
mean=np.mean(data)
max_val=np.max(data)
row_sum=np.sum(data,axis=1)
col_max=np.max(data,axis=0)
a=np.array([1,2,3,4,5])
mask=a>3
filter=a[a>3]
b=np.array([1,2,6,4,9])
condition=(a<b)&(a>2)
a=np.array([[1],[2],[3]])
b=np.array([10,20,30])
result_broadcast=a*b
arr=np.array([[1,2],[3,4]])
result_scalar_add=arr+10
print("zero:\n",zeros)
print("Ones:\n",ones)
print("Arrange:",arrange)
print("linspace:",linspace)

print("Addition:",add)
print("subtraction:",sub)
print("Multiplication:",mul)
print("Division:",power)

print("Negative:",neg)
print("square Root:",sqrt)

print("Total sum:",total_sum)
print("Mean:",mean)
print("Max Value:",max_val)
print("Row Sum:",row_sum)
print("Columum Max:",col_max)

print("boolean Mask(a>3):",mask)
print("Filtered values(a>3):",filter)
print("condition (a<6)&(a>2):",condition)

print("boardcasting result (a*b):\n",result_broadcast)
print("scalar addtion result (arr+10):\n",result_scalar_add)
